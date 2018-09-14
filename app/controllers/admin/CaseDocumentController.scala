package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.Files
import controllers.admin.CaseDocumentController._
import controllers.refiners.{CanEditCaseActionRefiner, CanViewCaseActionRefiner, CaseSpecificRequest}
import controllers.{BaseController, UploadedFileServing}
import domain._
import helpers.JavaTime
import helpers.ServiceResults.ServiceError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.CaseService

import scala.concurrent.{ExecutionContext, Future}

object CaseDocumentController {
  val form = Form(single(
    "documentType" -> CaseDocumentType.formField,
  ))

  def deleteForm(version: OffsetDateTime): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  ))
}

@Singleton
class CaseDocumentController @Inject()(
  cases: CaseService,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController with UploadedFileServing {

  import canEditCaseActionRefiner._
  import canViewCaseActionRefiner._

  def download(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    withCaseDocument(id) { doc =>
      serveFile(doc.file)
    }
  }

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

  def delete(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    withCaseDocument(id) { doc =>
      deleteForm(doc.lastUpdated).bindFromRequest().fold(
        formWithErrors => Future.successful(
          // Nowhere to show a validation error so just fall back to an error page
          showErrors(formWithErrors.errors.map { e => ServiceError(e.format) })
        ),
        version =>
          cases.deleteDocument(caseRequest.`case`.id.get, doc.id, version).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.documentDeleted"))
          }
      )
    }
  }

  private def withCaseDocument(id: UUID)(f: CaseDocument => Future[Result])(implicit caseRequest: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.getDocuments(caseRequest.`case`.id.get).successFlatMap { docs =>
      docs.find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

}
