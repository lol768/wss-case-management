package controllers.admin

import java.util.UUID

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.{BaseController, UploadedFileControllerHelper}
import controllers.refiners.{CanViewCaseActionRefiner, CaseMessageActionFilters, CaseSpecificRequest}
import domain.{IssueKey, MessageSave, MessageSender, UploadedFileSave}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.CaseService
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object CaseMessageController {
  val messageForm: Form[String] = TeamEnquiryController.messageForm
}

@Singleton
class CaseMessageController @Inject() (
  actions: CaseMessageActionFilters,
  canViewCase: CanViewCaseActionRefiner,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  caseController: CaseController
) (implicit
  executionContext: ExecutionContext,
  caseService: CaseService
) extends BaseController {

  import actions._
  import CaseMessageController._
  import canViewCase.CanViewCaseAction

  def addMessage(caseKey: IssueKey, client: UniversityID): Action[MultipartFormData[TemporaryUploadedFile]] = CanPostAsTeamAction(caseKey)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    messageForm.bindFromRequest.fold(addError, addSuccess(client))
  }

  def download(caseKey: IssueKey, fileId: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit request =>
    caseService.findFull(caseKey).successFlatMap { c =>
      c.messages.data.flatMap(_.files).find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  def addError(errors: Form[String])(implicit request: CaseSpecificRequest[_]): Future[Result] = {
    import request.{`case` => c}
    caseController.renderCase(
      c.key.get,
      CaseController.caseNoteFormPrefilled(c.version),
      errors
    )
  }

  def addSuccess(client: UniversityID)(text: String)(implicit request: CaseSpecificRequest[MultipartFormData[TemporaryUploadedFile]]): Future[Result] = {
    val message = messageSave(text, currentUser().usercode)
    val files = request.body.files.map(_.ref)
    caseService.addMessage(request.`case`, client, message, files.map(f => (f.in, f.metadata))).successMap { case (m, files) =>
      Redirect(controllers.admin.routes.CaseController.view(request.`case`.key.get).withFragment(s"thread-heading-${client.string}"))
    }
  }

  private def messageSave(text: String, teamMember: Usercode) = MessageSave(
    text = text,
    sender = MessageSender.Team,
    teamMember = Some(teamMember)
  )

}
