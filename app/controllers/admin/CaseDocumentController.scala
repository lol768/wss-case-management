package controllers.admin

import java.io.InputStream
import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.BaseController
import controllers.admin.CaseDocumentController._
import controllers.refiners.{CanEditCaseActionRefiner, CanViewCaseActionRefiner, CaseSpecificRequest}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.CaseService
import warwick.objectstore.ObjectStorageService

import scala.concurrent.{ExecutionContext, Future}

object CaseDocumentController {
  val form = Form(single(
    "documentType" -> CaseDocumentType.formField,
  ))
}

@Singleton
class CaseDocumentController @Inject()(
  cases: CaseService,
  objectStorageService: ObjectStorageService,
  temporaryFileCreator: TemporaryFileCreator,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canEditCaseActionRefiner._
  import canViewCaseActionRefiner._

  def addDocumentForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.addDocument(caseKey, form))
  }

  def addDocument(caseKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanEditCaseAction(caseKey)(parse.multipartFormData).async { implicit caseRequest =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.addDocument(caseKey, formWithErrors))
      ),
      documentType =>
        caseRequest.body.file("file").map { file =>
          // TODO should this add a case note?
          cases.addDocument(
            caseID = caseRequest.`case`.id.get,
            document = CaseDocumentSave(
              documentType,
              caseRequest.context.user.get.usercode
            ),
            in = Files.asByteSource(file.ref),
            file = UploadedFileSave(
              file.filename,
              file.ref.length(),
              file.contentType.getOrElse("application/octet-stream"),
              caseRequest.context.user.get.usercode
            )
          ).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.documentAdded"))
          }
        }.getOrElse(Future.successful(
          Ok(views.html.admin.cases.addDocument(caseKey, form.withError("file", "error.required")))
        ))
    )
  }

  def download(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    withCaseDocument(id) { doc =>
      val source = new ByteSource {
        override def openStream(): InputStream = objectStorageService.fetch(doc.file.id.toString).orNull
      }

      Future {
        val temporaryFile = temporaryFileCreator.create(prefix = doc.file.fileName, suffix = caseRequest.context.actualUser.get.usercode.string)
        val file = temporaryFile.path.toFile
        source.copyTo(Files.asByteSink(file))

        Ok.sendFile(content = file, fileName = _ => doc.file.fileName, onClose = () => temporaryFileCreator.delete(temporaryFile))
          .as(doc.file.contentType)
      }
    }
  }

  private def withCaseDocument(id: UUID)(f: CaseDocument => Future[Result])(implicit caseRequest: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.getDocuments(caseRequest.`case`.id.get).successFlatMap { docs =>
      docs.find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

}
