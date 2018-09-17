package controllers

import java.io.InputStream

import com.google.common.io.{ByteSource, Files}
import domain.UploadedFile
import javax.inject.{Inject, Singleton}
import play.api.http.FileMimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.mvc.Result
import play.api.mvc.Results._
import warwick.objectstore.ObjectStorageService
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

trait UploadedFileControllerHelper {
  def serveFile(uploadedFile: UploadedFile)(implicit request: AuthenticatedRequest[_]): Future[Result]
}

@Singleton
class UploadedFileControllerHelperImpl @Inject()(
  objectStorageService: ObjectStorageService,
  temporaryFileCreator: TemporaryFileCreator,
)(implicit executionContext: ExecutionContext, mimeTypes: FileMimeTypes) extends UploadedFileControllerHelper {

  def serveFile(uploadedFile: UploadedFile)(implicit request: AuthenticatedRequest[_]): Future[Result] = {
    val source = new ByteSource {
      override def openStream(): InputStream = objectStorageService.fetch(uploadedFile.id.toString).orNull
    }

    Future {
      val temporaryFile = temporaryFileCreator.create(prefix = uploadedFile.fileName, suffix = request.context.actualUser.get.usercode.string)
      val file = temporaryFile.path.toFile
      source.copyTo(Files.asByteSink(file))

      Ok.sendFile(content = file, fileName = _ => uploadedFile.fileName, onClose = () => temporaryFileCreator.delete(temporaryFile))
        .as(uploadedFile.contentType)
    }
  }

}
