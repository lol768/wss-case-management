package controllers.enquiries

import java.time.OffsetDateTime

import controllers.BaseController
import controllers.refiners.{CanAddMessageToEnquiryActionRefiner, CanEditEnquiryActionRefiner, CanViewEnquiryActionRefiner, EnquirySpecificRequest}
import domain._
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

object EnquiryMessagesController {
  case class StateChangeForm (
    text: String,
    version: OffsetDateTime
  )
}

@Singleton
class EnquiryMessagesController @Inject()(
  canViewEnquiryActionRefiner: CanViewEnquiryActionRefiner,
  canAddMessageToEnquiryActionRefiner: CanAddMessageToEnquiryActionRefiner,
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import EnquiryMessagesController.StateChangeForm

  def stateChangeForm(enquiry: Enquiry, sender: MessageSender) = Form(mapping(
    "text" -> text.verifying("missing", text => sender == MessageSender.Team || text.hasText),
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
  )(StateChangeForm.apply)(StateChangeForm.unapply))

  import canAddMessageToEnquiryActionRefiner._
  import canEditEnquiryActionRefiner._
  import canViewEnquiryActionRefiner._

  private def renderMessages(enquiry: Enquiry, messages: Seq[MessageData], f: Form[StateChangeForm])(implicit request: EnquirySpecificRequest[_]) =
    Ok(views.html.enquiry.messages(enquiry, messages, f, messageSender(request)))

  def messages(enquiryKey: IssueKey): Action[AnyContent] = CanViewEnquiryAction(enquiryKey) { implicit request =>
    renderMessages(request.enquiry, request.messages, stateChangeForm(request.enquiry, messageSender(request)).fill(StateChangeForm("", request.enquiry.version)))
  }

  def redirectToMessages(enquiryKey: IssueKey): Action[AnyContent] = Action {
    Redirect(routes.EnquiryMessagesController.messages(enquiryKey))
  }

  def addMessage(enquiryKey: IssueKey): Action[AnyContent] = CanAddMessageToEnquiryAction(enquiryKey).async { implicit request =>
    Form(single("text" -> nonEmptyText)).bindFromRequest().fold(
      formWithErrors => {
        val form = stateChangeForm(request.enquiry, messageSender(request))
          .fill(StateChangeForm(formWithErrors.value.getOrElse(""), request.enquiry.version))
          .copy(errors = formWithErrors.errors)
        Future.successful(renderMessages(request.enquiry, request.messages, form))
      },
      messageText => {
        val message = messageData(messageText, request)
        service.addMessage(request.enquiry, message).successMap { m =>
          renderMessages(request.enquiry, request.messages :+ MessageData(m.text, m.sender, m.created), stateChangeForm(request.enquiry, messageSender(request)))
        }
      }
    )
  }

  def close(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    updateStateAndMessage(IssueState.Closed)
  }

  def reopen(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    updateStateAndMessage(IssueState.Reopened)
  }

  private def updateStateAndMessage(newState: IssueState)(implicit request: EnquirySpecificRequest[_]): Future[Result] = {
    stateChangeForm(request.enquiry, messageSender(request)).bindFromRequest().fold(
      formWithErrors => Future.successful(renderMessages(request.enquiry, request.messages, formWithErrors)),
      formData => {
        val action = if(formData.text.hasText) {
          val message = messageData(formData.text, request)
          service.updateStateWithMessage(request.enquiry, newState, message, formData.version)
        } else {
          service.updateState(request.enquiry, newState, formData.version)
        }

        action.successMap { enquiry =>
          Redirect(routes.EnquiryMessagesController.messages(enquiry.key.get))
            .flashing("success" -> Messages(s"flash.enquiry.$newState"))
        }
      }
    )
  }

  private def messageData(text:String, request: EnquirySpecificRequest[_]) = {
    val sender = messageSender(request)

    domain.MessageSave(
      text = text,
      sender = sender,
      teamMember = if (sender == MessageSender.Team) request.context.user.map(_.usercode) else None
    )
  }

  private def messageSender(request: EnquirySpecificRequest[_]): MessageSender =
    if (request.context.user.flatMap(_.universityId).contains(request.enquiry.universityID))
      MessageSender.Client
    else
      MessageSender.Team
}
