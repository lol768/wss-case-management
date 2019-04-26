package controllers

import java.io.InputStream
import java.lang.{StringBuilder => JStringBuilder}
import java.util.{BitSet => JBitSet}

import akka.stream.scaladsl.FileIO
import com.google.common.io.{ByteSource, Files}
import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigMemorySize
import controllers.UploadedFileControllerHelper._
import domain.{UploadedFile, UploadedFileSave}
import javax.inject.{Inject, Named, Singleton}
import org.apache.tika.detect.DefaultDetector
import org.apache.tika.io.{IOUtils, TikaInputStream}
import org.apache.tika.metadata.{HttpHeaders, Metadata, TikaMetadataKeys}
import org.apache.tika.mime.{MediaType, MimeTypes}
import play.api.Configuration
import play.api.http.{FileMimeTypes, HeaderNames}
import play.api.libs.Files.TemporaryFileCreator
import play.api.mvc.Results._
import play.api.mvc._
import system.ImplicitRequestContext
import uk.ac.warwick.util.virusscan.{VirusScanResult, VirusScanService}
import warwick.core.Logging
import warwick.core.timing.{TimingCategories, TimingContext, TimingService}
import warwick.objectstore.ObjectStorageService
import warwick.sso.AuthenticatedRequest

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

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
  virusScanService: VirusScanService,
  parse: PlayBodyParsers,
  configuration: Configuration,
  @Named("objectStorage") objectStorageExecutionContext: ExecutionContext,
)(implicit executionContext: ExecutionContext, mimeTypes: FileMimeTypes)
  extends UploadedFileControllerHelper with ImplicitRequestContext with Logging {

  import timing._

  private[this] val config = UploadedFileConfiguration.fromConfiguration(configuration)

  override def serveFile(uploadedFile: UploadedFile)(implicit request: AuthenticatedRequest[_], t: TimingContext): Future[Result] = {
    val source = new ByteSource {
      override def openStream(): InputStream = timeSync(TimingCategories.ObjectStorageRead) {
        objectStorageService.fetch(uploadedFile.id.toString).orNull
      }
    }

    // Must be equal (if present) between 200 and 304:
    // Cache-Control, Content-Location, Date, ETag, Expires, and Vary
    // On production Vary is generated by the F5
    val cacheHeaders = Seq[(String, String)](
      HeaderNames.CACHE_CONTROL -> "private",
      HeaderNames.ETAG -> toEtag(uploadedFile),

      // Restrictive CSP, just enough for viewing inline
      HeaderNames.CONTENT_SECURITY_POLICY -> (
        "default-src 'none'; " +
        "img-src 'self' data:; " + // View images inline; allow data: for Safari media player
        "font-src 'self' data:; " + // Allow fonts in PDFs
        "object-src 'self'; " + // Allow plugins to load for the current context
        "plugin-types application/pdf; " + // Only allow the PDF plugin
        "style-src 'unsafe-inline'; " + // PDF viewer Chrome?
        "media-src 'self'" // Needed to load the audio/video
      ),

      // This currently stops Save As... working in Chrome
      // "Cross-Origin-Resource-Policy" -> "same-origin",
    )

    if (request.headers.get(HeaderNames.IF_NONE_MATCH).contains(toEtag(uploadedFile))) {
      Future.successful(NotModified.withHeaders(cacheHeaders: _*))
    } else {
      Future {
        val temporaryFile = temporaryFileCreator.create(prefix = uploadedFile.fileName, suffix = request.context.actualUser.get.usercode.string)
        source.copyTo(Files.asByteSink(temporaryFile.path.toFile))

        temporaryFile
      }(objectStorageExecutionContext).map { temporaryFile =>
        RangeResult.ofSource(
          entityLength = uploadedFile.contentLength,
          source = FileIO.fromPath(temporaryFile.path).mapMaterializedValue(_.onComplete { _ =>
            temporaryFileCreator.delete(temporaryFile)
          }),
          rangeHeader = request.headers.get(HeaderNames.RANGE),
          fileName = None, // We handle this ourselves below, as Play forces Content-Disposition: attachment
          contentType = Some(uploadedFile.contentType),
        ).withHeaders(cacheHeaders: _*)
         .withHeaders(HeaderNames.CONTENT_DISPOSITION -> {
           val builder = new JStringBuilder
           builder.append(if (config.isServeInline(MediaType.parse(uploadedFile.contentType))) "inline" else "attachment")
           builder.append("; ")
           HttpHeaderParameterEncoding.encodeToBuilder("filename", uploadedFile.fileName, builder)
           builder.toString
         })
      }
    }
  }

  // Yes, the actual value is supposed to include double quotes; such is the format for a strong Etag
  // Strong Etag means byte-for-byte the same, so it is allowed to process range requests from it etc.
  private def toEtag(uploadedFile: UploadedFile) = s""""${uploadedFile.id.toString}""""

  private def isOversized(file: TemporaryUploadedFile): Boolean =
    file.metadata.contentLength > config.maxIndividualFileSizeBytes

  private def isAllowedMimeType(file: TemporaryUploadedFile): Boolean =
    config.isAllowed(MediaType.parse(file.metadata.contentType))

  private def isVirus(file: TemporaryUploadedFile): Boolean = {
    val result = Await.result(virusScanService.scan(file.in).toScala, Duration.Inf)

    result.getStatus match {
      case VirusScanResult.Status.clean => false
      case VirusScanResult.Status.virus =>
        logger.warn(s"Virus uploaded: $file (${result.getVirus})")
        true
      case VirusScanResult.Status.error =>
        logger.warn(s"Error calling virus scan service for $file (${result.getError})")
        true
    }
  }

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
            }.getOrElse {
              files.find(isVirus).map { file =>
                Left(Results.UnprocessableEntity(views.html.errors.unprocessableEntity(file, config)))
              }.getOrElse(Right(body))
            }
          }
        }
    }

  override def supportedMimeTypes: Seq[MediaType] = config.allowedMimeTypes

}

/**
  * Grokked from play.core.utils.HttpHeaderParameterEncoding
  *
  * Support for rending HTTP header parameters according to RFC5987.
  */
private[controllers] object HttpHeaderParameterEncoding {
  private def charSeqToBitSet(chars: Seq[Char]): JBitSet = {
    val ints: Seq[Int] = chars.map(_.toInt)
    val max = ints.fold(0)(Math.max)
    assert(max <= 256) // We should only be dealing with 7 or 8 bit chars
    val bitSet = new JBitSet(max)
    ints.foreach(bitSet.set)
    bitSet
  }

  // From https://tools.ietf.org/html/rfc2616#section-2.2
  //
  //   separators     = "(" | ")" | "<" | ">" | "@"
  //                  | "," | ";" | ":" | "\" | <">
  //                  | "/" | "[" | "]" | "?" | "="
  //                  | "{" | "}" | SP | HT
  //
  // Rich: We exclude <">, "\" since they can be used for quoting/escaping and HT since it is
  // rarely used and seems like it should be escaped.
  private val Separators: Seq[Char] = Seq('(', ')', '<', '>', '@', ',', ';', ':', '/', '[', ']', '?', '=', '{', '}', ' ')

  private val AlphaNum: Seq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

  // From https://tools.ietf.org/html/rfc5987#section-3.2.1:
  //
  // attr-char     = ALPHA / DIGIT
  // / "!" / "#" / "$" / "&" / "+" / "-" / "."
  // / "^" / "_" / "`" / "|" / "~"
  // ; token except ( "*" / "'" / "%" )
  private val AttrCharPunctuation: Seq[Char] = Seq('!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~')

  /**
    * A subset of the 'qdtext' defined in https://tools.ietf.org/html/rfc2616#section-2.2. These are the
    * characters which can be inside a 'quoted-string' parameter value. These should form a
    * superset of the [[AttrChar]] set defined below. We exclude some characters which are technically
    * valid, but might be problematic, e.g. "\" and "%" could be treated as escape characters by some
    * clients. We can be conservative because we can express these characters clearly as an extended
    * parameter.
    */
  private val PartialQuotedText: JBitSet = charSeqToBitSet(
    AlphaNum ++ AttrCharPunctuation ++
      // we include 'separators' plus some chars excluded from 'attr-char'
      Separators ++ Seq('*', '\''))

  /**
    * The 'attr-char' values defined in https://tools.ietf.org/html/rfc5987#section-3.2.1. Should be a
    * subset of [[PartialQuotedText]] defined above.
    */
  private val AttrChar: JBitSet = charSeqToBitSet(AlphaNum ++ AttrCharPunctuation)

  private val PlaceholderChar: Char = '?'

  /**
    * Render a parameter name and value, handling character set issues as
    * recommended in RFC5987.
    *
    * Examples:
    * [[
    * render("filename", "foo.txt") ==> "filename=foo.txt"
    * render("filename", "naïve.txt") ==> "filename=na_ve.txt; filename*=utf8''na%C3%AFve.txt"
    * ]]
    */
  def encodeToBuilder(name: String, value: String, builder: JStringBuilder): Unit = {
    // This flag gets set if we encounter extended characters when rendering the
    // regular parameter value.
    var hasExtendedChars = false

    // Render ASCII parameter
    // E.g. naïve.txt --> "filename=na_ve.txt"

    builder.append(name)
    builder.append("=\"")

    // Iterate over code points here, because we only want one
    // ASCII character or placeholder per logical character. If
    // we use the value's encoded bytes or chars then we might
    // end up with multiple placeholders per logical character.
    value.codePoints().forEach { codePoint =>
      // We could support a wider range of characters here by using
      // the 'token' or 'quoted printable' encoding, however it's
      // simpler to use the subset of characters that is also valid
      // for extended attributes.
      if (codePoint >= 0 && codePoint <= 255 && PartialQuotedText.get(codePoint)) {
        builder.append(codePoint.toChar)
      } else {
        // Set flag because we need to render an extended parameter.
        hasExtendedChars = true
        // Render a placeholder instead of the unsupported character.
        builder.append(PlaceholderChar)
      }
    }

    builder.append('"')

    // Optionally render extended, UTF-8 encoded parameter
    // E.g. naïve.txt --> "; filename*=utf8''na%C3%AFve.txt"
    //
    // Renders both regular and extended parameters, as suggested by:
    // - https://tools.ietf.org/html/rfc5987#section-4.2
    // - https://tools.ietf.org/html/rfc6266#section-4.3 (for Content-Disposition filename parameter)

    if (hasExtendedChars) {
      def hexDigit(x: Int): Char = (if (x < 10) x + '0' else x - 10 + 'a').toChar

      // From https://tools.ietf.org/html/rfc5987#section-3.2.1:
      //
      // Producers MUST use either the "UTF-8" ([RFC3629]) or the "ISO-8859-1"
      // ([ISO-8859-1]) character set.  Extension character sets (mime-

      val CharacterSetName = "utf-8"

      builder.append("; ")
      builder.append(name)

      builder.append("*=")
      builder.append(CharacterSetName)
      builder.append("''")

      // From https://tools.ietf.org/html/rfc5987#section-3.2.1:
      //
      // Inside the value part, characters not contained in attr-char are
      // encoded into an octet sequence using the specified character set.
      // That octet sequence is then percent-encoded as specified in Section
      // 2.1 of [RFC3986].

      val bytes = value.getBytes(CharacterSetName)
      for (b <- bytes) {
        if (AttrChar.get(b & 0xFF)) {
          builder.append(b.toChar)
        } else {
          builder.append('%')
          builder.append(hexDigit((b >> 4) & 0xF))
          builder.append(hexDigit(b & 0xF))
        }
      }
    }
  }
}
