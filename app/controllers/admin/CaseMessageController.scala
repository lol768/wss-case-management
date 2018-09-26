package controllers.admin

import controllers.{BaseController, UploadedFileControllerHelper}
import controllers.refiners.CaseMessageActionFilters
import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import services.CaseService
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

object CaseMessageController {
  val messageForm: Form[String] = TeamEnquiryController.messageForm
}

@Singleton
class CaseMessageController @Inject() (
  actions: CaseMessageActionFilters,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
) (implicit
  executionContext: ExecutionContext,
  caseService: CaseService
) extends BaseController {

  import actions._

  def addMessage(caseKey: IssueKey, client: UniversityID) = CanPostAsTeamAction(caseKey)(uploadedFileControllerHelper.bodyParser) { implicit request =>
    // TODO try to reuse enquiry message adding stuff
    NotImplemented(views.html.defaultpages.todo())
  }

}
