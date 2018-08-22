package controllers.enquiries

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import domain.Message.{FormData => Data}
import domain.{Enquiry, EnquiryState, MessageData, MessageSender}
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

object EnquiryMessagesController {
  case class ReopenForm (
    text: String,
    version: OffsetDateTime
  )
}

@Singleton
class EnquiryMessagesController @Inject()(
  enquirySpecificActionRefiner: EnquirySpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import EnquiryMessagesController.ReopenForm

  val baseForm = Form(mapping(
    "text" -> nonEmptyText
  )(Data.apply)(Data.unapply))

  def reopenForm(enquiry: Enquiry, sender: MessageSender) = Form(mapping(
    "text" -> text.verifying("missing", text => sender == MessageSender.Team || text.hasText),
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
  )(ReopenForm.apply)(ReopenForm.unapply))

  import enquirySpecificActionRefiner._

  private def renderMessages(enquiry: Enquiry, messages: Seq[MessageData], f: Form[Data], rf: Form[ReopenForm])(implicit req: EnquirySpecificRequest[_]) =
    Ok(views.html.enquiry.messages(enquiry, messages, f, rf, messageSender(req)))

  def messages(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id) { implicit request =>
    renderMessages(request.enquiry, request.messages, baseForm, reopenForm(request.enquiry, messageSender(request)).fill(ReopenForm("", request.enquiry.version)))
  }

  def addMessage(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id).async { implicit request =>
    baseForm.bindFromRequest().fold(
      formWithErrors => Future.successful(renderMessages(request.enquiry, request.messages, formWithErrors, reopenForm(request.enquiry, messageSender(request)))),
      formData => {
        val message = messageData(formData.text, request)
        service.addMessage(request.enquiry, message).successMap { m =>
          renderMessages(request.enquiry, request.messages :+ MessageData(m.text, m.sender, m.created), baseForm, reopenForm(request.enquiry, messageSender(request)))
        }
      }
    )
  }

  def reopen(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id).async { implicit request =>
    reopenForm(request.enquiry, messageSender(request)).bindFromRequest().fold(
      formWithErrors => Future.successful(renderMessages(request.enquiry, request.messages, baseForm, formWithErrors)),
      formData => {
        val action = if(formData.text.hasText) {
          val message = messageData(formData.text, request)
          service.updateStateWithMessage(request.enquiry, EnquiryState.Reopened, message)
        } else {
          service.updateState(request.enquiry, EnquiryState.Reopened)
        }

        action.successMap { enquiry =>
          Redirect(routes.EnquiryMessagesController.messages(enquiry.id.get))
            .flashing("success" -> Messages("flash.enquiry.reopened"))
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
