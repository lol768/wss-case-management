package controllers

import java.io.InputStream

import com.google.common.io.{ByteSource, Files}
import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigMemorySize
import controllers.UploadedFileControllerHelper._
import domain.{UploadedFile, UploadedFileSave}
import javax.inject.{Inject, Singleton}
import org.apache.tika.detect.DefaultDetector
import org.apache.tika.io.{IOUtils, TikaInputStream}
import org.apache.tika.metadata.{HttpHeaders, Metadata, TikaMetadataKeys}
import org.apache.tika.mime.{MediaType, MimeTypes}
import play.api.Configuration
import play.api.http.{FileMimeTypes, HeaderNames}
import play.api.libs.Files.TemporaryFileCreator
import play.api.mvc.Results._
import play.api.mvc._
import system.{ImplicitRequestContext, TimingCategories}
import warwick.core.timing.{TimingContext, TimingService}
import warwick.objectstore.ObjectStorageService
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UploadedFileControllerHelperImpl])
trait UploadedFileControllerHelper {
  def serveFile(uploadedFile: UploadedFile)(implicit request: AuthenticatedRequest[_], t: TimingContext): Future[Result]
  def bodyParser: BodyParser[MultipartFormData[TemporaryUploadedFile]]
  def supportedMimeTypes: Seq[MediaType]
}

object UploadedFileControllerHelper {
  case class TemporaryUploadedFile(key: String, in: ByteSource, metadata: UploadedFileSave)

  case class UploadedFileConfiguration(
    maxTotalFileSizeBytes: Long,
    maxIndividualFileSizeBytes: Long,
    allowedMimeTypes: Seq[MediaType],
    serveInlineMimeTypes: Seq[MediaType]
  ) {
    def isAllowed(mediaType: MediaType): Boolean =
      allowedMimeTypes.exists { allowed =>
        allowed == mediaType || (allowed.getSubtype == "*" && allowed.getType == mediaType.getType)
      }

    def isServeInline(mediaType: MediaType): Boolean =
      serveInlineMimeTypes.exists { serveInline =>
        serveInline == mediaType || (serveInline.getSubtype == "*" && serveInline.getType == mediaType.getType)
      }
  }

  object UploadedFileConfiguration {
    def fromConfiguration(configuration: Configuration): UploadedFileConfiguration =
      UploadedFileConfiguration(
        maxTotalFileSizeBytes = configuration.get[ConfigMemorySize]("wellbeing.files.maxTotalFileSize").toBytes,
        maxIndividualFileSizeBytes = configuration.get[ConfigMemorySize]("wellbeing.files.maxIndividualFileSize").toBytes,
        allowedMimeTypes = configuration.get[Seq[String]]("wellbeing.files.allowedMimeTypes").map(MediaType.parse),
        serveInlineMimeTypes = configuration.get[Seq[String]]("wellbeing.files.serveInlineMimeTypes").map(MediaType.parse)
      )
  }

  /**
    * Uses the magic bytes at the start of the file first, then the filename, then the supplied content type.
    */
  private val mimeTypeDetector = new DefaultDetector(MimeTypes.getDefaultMimeTypes)

  def detectMimeType(file: TemporaryUploadedFile): MediaType = {
    val is = TikaInputStream.get(file.in.openStream())
    try {
      val metadata = new Metadata
      metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, file.metadata.fileName)
      metadata.set(HttpHeaders.CONTENT_TYPE, file.metadata.contentType)

      mimeTypeDetector.detect(is, metadata)
    } finally {
      IOUtils.closeQuietly(is)
    }
  }
}

@Singleton
class UploadedFileControllerHelperImpl @Inject()(
  objectStorageService: ObjectStorageService,
  temporaryFileCreator: TemporaryFileCreator,
  timing: TimingService,
  parse: PlayBodyParsers,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext, mimeTypes: FileMimeTypes)
  extends UploadedFileControllerHelper with ImplicitRequestContext {

  import timing._

  private[this] val config = UploadedFileConfiguration.fromConfiguration(configuration)

  override def serveFile(uploadedFile: UploadedFile)(implicit request: AuthenticatedRequest[_], t: TimingContext): Future[Result] = {
    val source = new ByteSource {
      override def openStream(): InputStream = timeSync(TimingCategories.ObjectStorageRead) {
        objectStorageService.fetch(uploadedFile.id.toString).orNull
      }
    }

    if (request.headers.get(HeaderNames.IF_NONE_MATCH).contains(toEtag(uploadedFile))) {
      Future.successful(NotModified)
    } else {
      Future {
        val temporaryFile = temporaryFileCreator.create(prefix = uploadedFile.fileName, suffix = request.context.actualUser.get.usercode.string)
        val file = temporaryFile.path.toFile
        source.copyTo(Files.asByteSink(file))

        Ok.sendFile(
          content = file,
          inline = config.isServeInline(MediaType.parse(uploadedFile.contentType)),
          fileName = _ => uploadedFile.fileName,
          onClose = () => temporaryFileCreator.delete(temporaryFile))
          .as(uploadedFile.contentType)
          .withHeaders(
            HeaderNames.CACHE_CONTROL -> "private",
            HeaderNames.ETAG -> toEtag(uploadedFile)
          )
      }
    }
  }

  private def toEtag(uploadedFile: UploadedFile) = s""""${uploadedFile.id.toString}""""

  private def isOversized(file: TemporaryUploadedFile): Boolean =
    file.metadata.contentLength > config.maxIndividualFileSizeBytes

  private def isAllowedMimeType(file: TemporaryUploadedFile): Boolean =
    config.isAllowed(MediaType.parse(file.metadata.contentType))

  override def bodyParser: BodyParser[MultipartFormData[TemporaryUploadedFile]] =
    parse.using { request =>
      parse.multipartFormData
        .map { body =>
          body.copy(files = body.files.filter(_.filename.nonEmpty).map { file =>
            val uploadedFile =
              TemporaryUploadedFile(
                file.key,
                Files.asByteSource(file.ref),
                UploadedFileSave(
                  file.filename,
                  file.ref.length(),
                  file.contentType.getOrElse("application/octet-stream"),
                )
              )

            // Don't blindly use the declared mime type, use Tika to detect
            val detectedMimeType = detectMimeType(uploadedFile).getBaseType.toString

            file.copy(ref = uploadedFile.copy(metadata = uploadedFile.metadata.copy(contentType = detectedMimeType)))
          })
        }
        .validate { body =>
          // We don't need to validate maxTotalFileSize here because that's done in parse.multipartFormData
          val files = body.files.map(_.ref)

          implicit val context: RequestContext = requestContext(request)

          files.find(isOversized).map { file =>
            Left(Results.EntityTooLarge(views.html.errors.entityTooLarge(file, config)))
          }.getOrElse {
            files.find(!isAllowedMimeType(_)).map { file =>
              Left(Results.UnsupportedMediaType(views.html.errors.unsupportedMediaType(file, config)))
            }.getOrElse(Right(body))
          }
        }
    }

  override def supportedMimeTypes: Seq[MediaType] = config.allowedMimeTypes

}
