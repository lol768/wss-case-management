package controllers.enquiries

import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.enquiries.EnquiryMessagesController._
import controllers.refiners.{CanAddClientMessageToEnquiryActionRefiner, CanClientViewEnquiryActionRefiner, EnquirySpecificRequest}
import controllers.{API, BaseController, UploadedFileServing}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
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
  userLookupService: UserLookupService,
)(implicit executionContext: ExecutionContext) extends BaseController with UploadedFileServing {

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

  def addMessage(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanAddClientMessageToEnquiryAction(enquiryKey)(parse.multipartFormData).async { implicit request =>
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
        val file = uploadedFile(request)

        service.addMessage(request.enquiry, message, file).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, m.created, m.teamMember)

          render {
            case Accepts.Json() =>
              val clientName = "You"
              val teamName = request.enquiry.team.name

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "message" -> views.html.enquiry.enquiryMessage(request.enquiry, messageData, f, clientName, teamName, f => routes.EnquiryMessagesController.download(enquiryKey, f.id)).toString()
              ))))
            case _ =>
              Redirect(routes.EnquiryMessagesController.messages(enquiryKey))
          }
        }
      }
    )
  }

  def download(enquiryKey: IssueKey, fileId: UUID): Action[AnyContent] = CanClientViewEnquiryAction(enquiryKey).async { implicit request =>
    service.getForRender(request.enquiry.id.get).successFlatMap { case (_, messages) =>
      messages.flatMap { case (_, f) => f }.find(_.id == fileId)
        .map(serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  private def messageData(text:String, request: EnquirySpecificRequest[_]): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Client,
      teamMember = None
    )

  private def uploadedFile(request: EnquirySpecificRequest[MultipartFormData[TemporaryFile]]): Option[(ByteSource, UploadedFileSave)] =
    request.body.file("file").filter(_.filename.nonEmpty).map { file =>
      (Files.asByteSource(file.ref), UploadedFileSave(
        file.filename,
        file.ref.length(),
        file.contentType.getOrElse("application/octet-stream"),
        request.context.user.get.usercode
      ))
    }
}
