package controllers.enquiries

import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.enquiries.EnquiryMessagesController._
import controllers.refiners.{CanAddClientMessageToEnquiryActionRefiner, CanClientViewEnquiryActionRefiner, EnquirySpecificRequest}
import controllers.{API, BaseController, UploadedFileControllerHelper}
import domain._
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.tabula.ProfileService
import services.{AuditService, EnquiryService}
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
  profiles: ProfileService,
  audit: AuditService,
  userLookupService: UserLookupService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canAddClientMessageToEnquiryActionRefiner._
  import canClientViewEnquiryActionRefiner._

  private def renderMessages(enquiry: Enquiry, f: Form[String])(implicit request: EnquirySpecificRequest[_]): Future[Result] =
    ServiceResults.zip(
      service.getForRender(enquiry.id.get),
      profiles.getProfile(enquiry.universityID).map(_.value),
    ).successMap { case (render, client) =>
      Ok(views.html.enquiry.messages(
        render.enquiry,
        client,
        render.messages,
        f
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
                formWithErrors.errors.map(error => API.Error(error.getClass.getSimpleName, error.format))
              )))
            )
          case _ =>
            renderMessages(request.enquiry, formWithErrors)
        }
      },
      messageText => {
        val message = messageData(messageText, request)
        val files = uploadedFiles(request)

        service.addMessage(request.enquiry, message, files).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, m.created, m.teamMember, m.team)

          render {
            case Accepts.Json() =>
              val clientName = "You"
              val teamName = s"${messageData.team.getOrElse(request.enquiry.team).name} team"

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

  def auditView(enquiryKey: IssueKey): Action[AnyContent] = CanClientViewEnquiryAction(enquiryKey).async { implicit request =>
    audit.audit('EnquiryView, request.enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      Future.successful(Right(
        render {
          case Accepts.Json() =>
            Accepted(Json.toJson(API.Success[JsObject](data = Json.obj())))
          case _ =>
            Redirect(routes.EnquiryMessagesController.messages(enquiryKey))
        }
      ))
    }.successMap(identity)
  }

  def download(enquiryKey: IssueKey, fileId: UUID): Action[AnyContent] = CanClientViewEnquiryAction(enquiryKey).async { implicit request =>
    service.getForRender(request.enquiry.id.get).successFlatMap { render =>
      render.messages.flatMap(_.files).find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  private def messageData(text:String, request: EnquirySpecificRequest[_]): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Client,
      teamMember = None
    )

  private def uploadedFiles(request: EnquirySpecificRequest[MultipartFormData[TemporaryFile]]): Seq[(ByteSource, UploadedFileSave)] =
    request.body.files.filter(_.filename.nonEmpty).map { file =>
      (Files.asByteSource(file.ref), UploadedFileSave(
        file.filename,
        file.ref.length(),
        file.contentType.getOrElse("application/octet-stream"),
        request.context.user.get.usercode
      ))
    }
}
