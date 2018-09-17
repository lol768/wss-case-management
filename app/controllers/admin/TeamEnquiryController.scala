package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.admin.TeamEnquiryController._
import controllers.refiners.{CanAddTeamMessageToEnquiryActionRefiner, CanEditEnquiryActionRefiner, CanViewEnquiryActionRefiner, EnquirySpecificRequest}
import controllers.{API, BaseController, UploadedFileControllerHelper}
import domain._
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.EnquiryService
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

object TeamEnquiryController {
  case class ReassignEnquiryData(
    team: Team,
    version: OffsetDateTime
  )

  def reassignEnquiryForm(enquiry: Enquiry) = Form(
    mapping(
      "team" -> Teams.formField,
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
    )(ReassignEnquiryData.apply)(ReassignEnquiryData.unapply)
  )

  case class StateChangeForm (
    text: String,
    version: OffsetDateTime
  )

  def stateChangeForm(enquiry: Enquiry) = Form(mapping(
    "text" -> text,
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
  )(StateChangeForm.apply)(StateChangeForm.unapply))
}

@Singleton
class TeamEnquiryController @Inject()(
  canAddTeamMessageToEnquiryActionRefiner: CanAddTeamMessageToEnquiryActionRefiner,
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  canViewEnquiryActionRefiner: CanViewEnquiryActionRefiner,
  service: EnquiryService,
  userLookupService: UserLookupService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canAddTeamMessageToEnquiryActionRefiner._
  import canEditEnquiryActionRefiner._
  import canViewEnquiryActionRefiner._

  private def renderMessages(enquiry: Enquiry, f: Form[StateChangeForm])(implicit request: EnquirySpecificRequest[_]): Future[Result] =
    service.getForRender(enquiry.id.get).successMap { render =>
      Ok(views.html.admin.enquiry.messages(
        render.enquiry,
        render.messages,
        f,
        userLookupService.getUsers(render.messages.flatMap { case (m, _) => m.teamMember }).toOption.getOrElse(Map()),
        userLookupService.getUsers(Seq(render.enquiry.universityID)).toOption.getOrElse(Map())
      ))
    }

  def messages(enquiryKey: IssueKey): Action[AnyContent] = CanViewEnquiryAction(enquiryKey).async { implicit request =>
    renderMessages(request.enquiry, stateChangeForm(request.enquiry).fill(StateChangeForm("", request.enquiry.version)))
  }

  def redirectToMessages(enquiryKey: IssueKey): Action[AnyContent] = Action {
    Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
  }

  def addMessage(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanAddTeamMessageToEnquiryAction(enquiryKey)(parse.multipartFormData).async { implicit request =>
    Form(single("text" -> nonEmptyText)).bindFromRequest().fold(
      formWithErrors => {
        val form = stateChangeForm(request.enquiry)
          .fill(StateChangeForm(formWithErrors.value.getOrElse(""), request.enquiry.version))
          .copy(errors = formWithErrors.errors)

        render.async {
          case Accepts.Json() =>
            Future.successful(
              BadRequest(Json.toJson(API.Failure[JsObject]("bad_request",
                form.errors.map(error => API.Error(error.getClass.getSimpleName, error.message))
              )))
            )
          case _ =>
            renderMessages(request.enquiry, form)
        }
      },
      messageText => {
        val message = messageData(messageText, request)
        val files = uploadedFiles(request)

        service.addMessage(request.enquiry, message, files).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, m.created, m.teamMember)
          render {
            case Accepts.Json() =>
              val clientName = "Client"
              val teamName = message.teamMember.flatMap(usercode => userLookupService.getUser(usercode).toOption.filter(_.isFound).flatMap(_.name.full)).getOrElse(request.enquiry.team.name)

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "message" -> views.html.enquiry.enquiryMessage(request.enquiry, messageData, f, clientName, teamName, f => routes.TeamEnquiryController.download(enquiryKey, f.id)).toString()
              ))))
            case _ =>
              Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
          }
        }
      }
    )
  }

  def download(enquiryKey: IssueKey, fileId: UUID): Action[AnyContent] = CanViewEnquiryAction(enquiryKey).async { implicit request =>
    service.getForRender(request.enquiry.id.get).successFlatMap { render =>
      render.messages.flatMap { case (_, f) => f }.find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  def close(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanEditEnquiryAction(enquiryKey)(parse.multipartFormData).async { implicit request =>
    updateStateAndMessage(IssueState.Closed)
  }

  def reopen(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanEditEnquiryAction(enquiryKey)(parse.multipartFormData).async { implicit request =>
    updateStateAndMessage(IssueState.Reopened)
  }

  private def updateStateAndMessage(newState: IssueState)(implicit request: EnquirySpecificRequest[MultipartFormData[TemporaryFile]]): Future[Result] = {
    stateChangeForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => renderMessages(request.enquiry, formWithErrors),
      formData => {
        val action = if(formData.text.hasText) {
          val message = messageData(formData.text, request)
          val files = uploadedFiles(request)

          service.updateStateWithMessage(request.enquiry, newState, message, files, formData.version)
        } else {
          service.updateState(request.enquiry, newState, formData.version)
        }

        action.successMap { enquiry =>
          Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiry.key.get))
            .flashing("success" -> Messages(s"flash.enquiry.$newState"))
        }
      }
    )
  }

  private def messageData(text: String, request: EnquirySpecificRequest[_]): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Team,
      teamMember = request.context.user.map(_.usercode)
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

  def reassignForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey) { implicit request =>
    Ok(views.html.admin.enquiry.reassign(request.enquiry, reassignEnquiryForm(request.enquiry).fill(ReassignEnquiryData(request.enquiry.team, request.enquiry.version))))
  }

  def reassign(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    reassignEnquiryForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.enquiry.reassign(
          request.enquiry,
          formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", request.enquiry.version))
        ))
      ),
      data =>
        if (data.team == request.enquiry.team) // No change
          Future.successful(Redirect(controllers.admin.routes.AdminController.teamHome(data.team.id)))
        else
          service.reassign(request.enquiry, data.team, data.version).successMap { _ =>
            Redirect(controllers.admin.routes.AdminController.teamHome(request.enquiry.team.id))
              .flashing("success" -> Messages("flash.enquiry.reassigned", data.team.name))
          }
    )
  }

}
