package controllers

import java.time.OffsetDateTime
import java.util.UUID

import controllers.MessagesController.MessageFormData
import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.refiners.{ClientIssueActionFilters, IssueSpecificRequest}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.tabula.ProfileService
import services.{AuditService, CaseService, EnquiryService}
import warwick.core.helpers.JavaTime
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

object MessagesController {
  case class MessageFormData(
    text: String,
    lastMessage: Option[OffsetDateTime]
  )

  def messageForm(lastMessageDate: Option[OffsetDateTime]) = Form(mapping(
    "text" -> nonEmptyText,
    "lastMessage" -> optional(JavaTime.offsetDateTimeFormField), // Don't optimistic lock clients .verifying("error.optimisticLocking", _ == lastMessageDate)
  )(MessageFormData.apply)(MessageFormData.unapply))
}

@Singleton
class ClientMessagesController @Inject()(
  canClientViewIssueActionRefiner: ClientIssueActionFilters,
  enquiryService: EnquiryService,
  caseService: CaseService,
  profiles: ProfileService,
  audit: AuditService,
  userLookupService: UserLookupService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canClientViewIssueActionRefiner._

  private def renderMessages(issue: Issue, f: Form[MessageFormData])(implicit request: IssueSpecificRequest[_]): Future[Result] =
    matchIssue(
      issue,
      _ => enquiryService.getForRender(issue.id).map(_.map(_.toIssue)),
      _ => caseService.findForClient(issue.id, currentUser.universityId.get).map(_.map(_.toIssue))
    ).successMap(issueRender =>
      render {
        case Accepts.Json() =>
          val clientName = "You"

          Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
            "lastMessage" -> issueRender.messages.lastOption.map(_.message.created),
            "lastMessageRelative" -> issueRender.messages.lastOption.map(_.message.created).map(JavaTime.Relative.apply(_)),
            "awaiting" -> issueRender.messages.lastOption.map(_.message.sender == MessageSender.Team),
            "messagesHTML" -> issueRender.messages.map { m =>
              val teamName = m.message.team.getOrElse(request.issue.team).name
              views.html.tags.messages.message(m.message, m.files, clientName, teamName, f => routes.ClientMessagesController.download(issue.id, f.id)).toString()
            }.mkString("")
          ))))
        case _ =>
          Ok(views.html.clientMessages(
            issueRender,
            f,
            uploadedFileControllerHelper.supportedMimeTypes
          ))
      }
    )

  def messages(id: java.util.UUID): Action[AnyContent] = CanClientViewIssueAction(id).async { implicit request =>
    renderMessages(request.issue, MessagesController.messageForm(request.lastMessageDate).fill(MessageFormData("", request.lastMessageDate)))
  }

  def addMessage(id: java.util.UUID): Action[MultipartFormData[TemporaryUploadedFile]] = CanAddClientMessageToIssueAction(id)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    MessagesController.messageForm(request.lastMessageDate).bindFromRequest().fold(
      formWithErrors => {
        render.async {
          case Accepts.Json() =>
            Future.successful(API.badRequestJson(formWithErrors)
            )
          case _ =>
            renderMessages(request.issue, formWithErrors)
        }
      },
      messageFormData => {
        val message = messageData(messageFormData.text)
        val files = request.body.files.map(_.ref)

        matchIssue(
          request.issue,
          e => enquiryService.addMessage(e, message, files.map { f => (f.in, f.metadata) }),
          c => caseService.addMessage(c, currentUser.universityId.get, message, files.map { f => (f.in, f.metadata) })
        ).successMap { case (messageData, f) =>
          render {
            case Accepts.Json() =>
              val clientName = "You"
              val teamName = messageData.team.getOrElse(request.issue.team).name

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "lastMessage" -> messageData.created,
                "lastMessageRelative" ->JavaTime.Relative(messageData.created),
                "message" -> views.html.tags.messages.message(messageData, f, clientName, teamName, f => routes.ClientMessagesController.download(id, f.id)).toString()
              ))))
            case _ =>
              Redirect(routes.ClientMessagesController.messages(id))
          }
        }
      }
    )
  }

  def auditView(id: java.util.UUID): Action[AnyContent] = CanClientViewIssueAction(id).async { implicit request =>
    audit.audit(
      matchIssue(request.issue, _ => 'EnquiryView, _ => 'CaseView),
      request.issue.id.toString,
      matchIssue(request.issue, _ => 'Enquiry, _ => 'Case),
      Json.obj()
    ) {
      Future.successful(Right(
        render {
          case Accepts.Json() =>
            Accepted(Json.toJson(API.Success[JsObject](data = Json.obj())))
          case _ =>
            Redirect(routes.ClientMessagesController.messages(id))
        }
      ))
    }.successMap(identity)
  }

  def download(id: java.util.UUID, fileId: UUID): Action[AnyContent] = CanClientViewIssueAction(id).async { implicit request =>
    matchIssue(
      request.issue,
      _ => enquiryService.getForRender(request.issue.id).map(_.map(_.messages)),
      _ => caseService.findForClient(request.issue.id, currentUser.universityId.get).map(_.map(_.messages))
    ).successFlatMap { messages =>
      messages.flatMap(_.files).find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  private def messageData(text: String): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Client,
      teamMember = None
    )

  private def matchIssue[A](issue: Issue, enquiryAction: Enquiry => A, caseAction: Case => A): A = issue match {
    case e: Enquiry => enquiryAction(e)
    case c: Case => caseAction(c)
    case _ => throw new IllegalArgumentException
  }
}
