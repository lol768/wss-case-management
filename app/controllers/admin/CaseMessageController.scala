package controllers.admin

import java.util.UUID

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.refiners.{CanViewCaseActionRefiner, CaseMessageActionFilters}
import controllers.{API, BaseController, UploadedFileControllerHelper}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.{AuditService, CaseService}
import warwick.sso.{UniversityID, UserLookupService, Usercode}

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
  caseService: CaseService,
  userLookupService: UserLookupService,
  auditService: AuditService
) extends BaseController {

  import CaseMessageController._
  import actions._
  import canViewCase.CanViewCaseAction

  def addMessage(caseKey: IssueKey, client: UniversityID): Action[MultipartFormData[TemporaryUploadedFile]] = CanPostAsTeamAction(caseKey)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    messageForm.bindFromRequest().fold(
      formWithErrors => {
        render.async {
          case Accepts.Json() =>
            Future.successful(API.badRequestJson(formWithErrors))
          case _ =>
            import request.{`case` => c}
            caseController.renderCase(
              c.key.get,
              formWithErrors
            )
        }
      },
      messageText => {
        val message = messageSave(messageText, currentUser().usercode)
        val files = request.body.files.map(_.ref)

        caseService.addMessage(request.`case`, client, message, files.map(f => (f.in, f.metadata))).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, client, m.created, m.teamMember, m.team)
          render {
            case Accepts.Json() =>
              val teamName = message.teamMember.flatMap(usercode => userLookupService.getUser(usercode).toOption.filter(_.isFound).flatMap(_.name.full)).getOrElse(request.`case`.team.name)

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "message" -> views.html.tags.messages.message(messageData, f, "Client", teamName, f => routes.CaseMessageController.download(caseKey, f.id)).toString()
              ))))
            case _ =>
              Redirect(controllers.admin.routes.CaseController.view(request.`case`.key.get).withFragment(s"thread-heading-${client.string}"))
          }
        }
      }
    )
  }

  def download(caseKey: IssueKey, fileId: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit request =>
    caseService.getCaseMessages(request.`case`.id.get).successFlatMapTo(messages =>
      messages.data.flatMap(_.files).find(_.id == fileId)
        .map(f => auditService.audit('CaseDocumentDownload, fileId.toString, 'CaseDocument, Json.obj()) {
          uploadedFileControllerHelper.serveFile(f).map(Right.apply)
        })
        .getOrElse(Future.successful(Right(NotFound(views.html.errors.notFound()))))
    ).successMap(r => r)
  }

  private def messageSave(text: String, teamMember: Usercode) = MessageSave(
    text = text,
    sender = MessageSender.Team,
    teamMember = Some(teamMember)
  )

}
