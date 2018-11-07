package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.admin.CaseDocumentController._
import controllers.refiners.{CanEditCaseActionRefiner, CanViewCaseActionRefiner, CaseSpecificRequest}
import controllers.{BaseController, UploadedFileControllerHelper}
import domain._
import helpers.ServiceResults.ServiceError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.CaseService
import warwick.core.helpers.JavaTime

import scala.concurrent.{ExecutionContext, Future}

object CaseDocumentController {

  case class DocumentForm(documentType: CaseDocumentType, description: String)

  val form = Form(mapping(
    "documentType" -> CaseDocumentType.formField,
    "description" -> nonEmptyText
  )(DocumentForm.apply)(DocumentForm.unapply))

  def deleteForm(version: OffsetDateTime): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  ))
}

@Singleton
class CaseDocumentController @Inject()(
  cases: CaseService,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canEditCaseActionRefiner._
  import canViewCaseActionRefiner._

  def download(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    withCaseDocument(id) { doc =>
      uploadedFileControllerHelper.serveFile(doc.file)
    }
  }

  def addDocumentForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.addDocument(caseKey, form, uploadedFileControllerHelper.supportedMimeTypes))
  }

  def addDocument(caseKey: IssueKey): Action[MultipartFormData[TemporaryUploadedFile]] = CanEditCaseAction(caseKey)(uploadedFileControllerHelper.bodyParser).async { implicit caseRequest =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.addDocument(caseKey, formWithErrors, uploadedFileControllerHelper.supportedMimeTypes))
      ),
      formData =>
        caseRequest.body.file("file").map { file =>
          cases.addDocument(
            caseID = caseRequest.`case`.id,
            document = CaseDocumentSave(
              formData.documentType,
              caseRequest.context.user.get.usercode
            ),
            in = file.ref.in,
            file = file.ref.metadata,
            caseNote = CaseNoteSave(formData.description, caseRequest.context.user.get.usercode)
          ).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.documentAdded"))
          }
        }.getOrElse(Future.successful(
          Ok(views.html.admin.cases.addDocument(caseKey, form.withError("file", "error.required"), uploadedFileControllerHelper.supportedMimeTypes))
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
          cases.deleteDocument(caseRequest.`case`.id, doc.id, version).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.documentDeleted"))
          }
      )
    }
  }

  private def withCaseDocument(id: UUID)(f: CaseDocument => Future[Result])(implicit caseRequest: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.getDocuments(caseRequest.`case`.id).successFlatMap { docs =>
      docs.find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

}
