package controllers.enquiries

import controllers.enquiries.EnquiryMessagesController._
import controllers.refiners.{CanAddClientMessageToEnquiryActionRefiner, CanClientViewEnquiryActionRefiner, EnquirySpecificRequest}
import controllers.{API, BaseController}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Result}
import services.EnquiryService
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

object EnquiryMessagesController {
  val form = Form(single("text" -> nonEmptyText))
}

@Singleton
class EnquiryMessagesController @Inject()(
  canClientViewEnquiryActionRefiner: CanClientViewEnquiryActionRefiner,
  canAddClientMessageToEnquiryActionRefiner: CanAddClientMessageToEnquiryActionRefiner,
  service: EnquiryService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canAddClientMessageToEnquiryActionRefiner._
  import canClientViewEnquiryActionRefiner._

  private def renderMessages(enquiry: Enquiry, f: Form[String])(implicit request: EnquirySpecificRequest[_]): Future[Result] =
    service.getForRender(enquiry.id.get).successMap { case (e, messages) =>
      Ok(views.html.enquiry.messages(
        e,
        messages,
        f,
        userLookupService.getUsers(Seq(e.universityID)).toOption.getOrElse(Map())
      ))
    }

  def messages(enquiryKey: IssueKey): Action[AnyContent] = CanClientViewEnquiryAction(enquiryKey).async { implicit request =>
    renderMessages(request.enquiry, form)
  }

  def addMessage(enquiryKey: IssueKey): Action[AnyContent] = CanAddClientMessageToEnquiryAction(enquiryKey).async { implicit request =>
    Form(single("text" -> nonEmptyText)).bindFromRequest().fold(
      formWithErrors => {
        render.async {
          case Accepts.Json() =>
            Future.successful(
              BadRequest(Json.toJson(API.Failure[JsObject]("bad_request",
                formWithErrors.errors.map(error => API.Error(error.getClass.getSimpleName, error.message))
              )))
            )
          case _ =>
            renderMessages(request.enquiry, formWithErrors)
        }
      },
      messageText => {
        val message = messageData(messageText, request)
        service.addMessage(request.enquiry, message).successMap { m =>
          val messageData = MessageData(m.text, m.sender, m.created, m.teamMember)

          render {
            case Accepts.Json() =>
              val clientName = "You"
              val teamName = request.enquiry.team.name

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "message" -> views.html.enquiry.enquiryMessage(request.enquiry, messageData, clientName, teamName).toString()
              ))))
            case _ =>
              Redirect(routes.EnquiryMessagesController.messages(enquiryKey))
          }
        }
      }
    )
  }

  private def messageData(text:String, request: EnquirySpecificRequest[_]): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Client,
      teamMember = None
    )
}
