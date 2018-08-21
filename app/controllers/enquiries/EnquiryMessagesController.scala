package controllers.enquiries

import java.util.UUID

import controllers.BaseController
import domain.Message.{FormData => Data}
import domain.{Enquiry, MessageData, MessageSender}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, RequestHeader}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryMessagesController @Inject()(
  enquirySpecificActionRefiner: EnquirySpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  val baseForm = Form(mapping(
    "text" -> nonEmptyText
  )(Data.apply)(Data.unapply))

  import enquirySpecificActionRefiner._

  private def render(enquiry: Enquiry, messages: Seq[MessageData], f: Form[Data])(implicit req: EnquirySpecificRequest[_]) =
    Ok(views.html.enquiry.messages(enquiry, messages, f, messageSender(req)))

  def messages(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id) { implicit request =>
    render(request.enquiry, request.messages, baseForm)
  }

  def addMessage(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id).async { implicit request =>
    baseForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(request.enquiry, request.messages, formWithErrors)),
      formData => {
        val sender = messageSender(request)

        val message = domain.MessageSave(
          text = formData.text,
          sender = sender,
          teamMember = if (sender == MessageSender.Team) request.context.user.map(_.usercode) else None
        )

        service.addMessage(request.enquiry, message).successMap { m =>
          render(request.enquiry, request.messages :+ MessageData(m.text, m.sender, m.created), baseForm)
        }
      }
    )
  }

  private def messageSender(request: EnquirySpecificRequest[_]): MessageSender =
    if (request.context.user.flatMap(_.universityId).contains(request.enquiry.universityID))
      MessageSender.Client
    else
      MessageSender.Team
}
