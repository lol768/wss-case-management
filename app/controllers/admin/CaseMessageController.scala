package controllers.admin

import java.nio.file.Files

import controllers.BaseController
import controllers.refiners.{CaseMessageActionFilters, CaseSpecificRequest}
import domain.{IssueKey, MessageSave, MessageSender, UploadedFileSave}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{MultipartFormData, Result}
import services.CaseService
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object CaseMessageController {
  val messageForm: Form[String] = TeamEnquiryController.messageForm
}

@Singleton
class CaseMessageController @Inject() (
  actions: CaseMessageActionFilters,
  caseController: CaseController
) (implicit
  executionContext: ExecutionContext,
  caseService: CaseService
) extends BaseController {

  import actions._
  import CaseMessageController._

  def addMessage(caseKey: IssueKey, client: UniversityID) = CanPostAsTeamAction(caseKey)(parse.multipartFormData).async { implicit request =>
    messageForm.bindFromRequest.fold(addError, addSuccess(client))
  }

  def addError(errors: Form[String])(implicit request: CaseSpecificRequest[_]): Future[Result] = {
    import request.{`case` => c}
    caseController.renderCase(
      c.key.get,
      CaseController.caseNoteFormPrefilled(c.version),
      errors
    )
  }

  def addSuccess(client: UniversityID)(text: String)(implicit request: CaseSpecificRequest[MultipartFormData[TemporaryFile]]): Future[Result] = {
    val message = messageSave(text, currentUser().usercode)
    val files = UploadedFileSave.seqFromRequest(request)
    caseService.addMessage(request.`case`, client, message, files).successMap { case (m, files) =>
      Redirect(controllers.admin.routes.CaseController.view(request.`case`.key.get).withFragment(""))
    }
  }

  private def messageSave(text: String, teamMember: Usercode) = MessageSave(
    text = text,
    sender = MessageSender.Team,
    teamMember = Some(teamMember)
  )

}
