package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.admin.TeamEnquiryController._
import controllers.refiners.{CanAddTeamMessageToEnquiryActionRefiner, CanEditEnquiryActionRefiner, CanViewEnquiryActionRefiner, EnquirySpecificRequest}
import controllers.{API, BaseController, UploadedFileControllerHelper}
import domain._
import helpers.ServiceResults.ServiceResult
import helpers.{JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.{CaseService, EnquiryService, PermissionService}
import services.tabula.ProfileService
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

object TeamEnquiryController {
  case class ReassignEnquiryData(
    team: Team,
    version: OffsetDateTime,
    message: String
  )

  def reassignEnquiryForm(enquiry: Enquiry): Form[ReassignEnquiryData] = Form(
    mapping(
      "team" -> Teams.formField,
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version),
      "message" -> nonEmptyText
    )(ReassignEnquiryData.apply)(ReassignEnquiryData.unapply)
  )

  def stateChangeForm(enquiry: Enquiry): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
  ))

  val messageForm = Form(single("text" -> nonEmptyText))
}

@Singleton
class TeamEnquiryController @Inject()(
  canAddTeamMessageToEnquiryActionRefiner: CanAddTeamMessageToEnquiryActionRefiner,
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  canViewEnquiryActionRefiner: CanViewEnquiryActionRefiner,
  service: EnquiryService,
  userLookupService: UserLookupService,
  profiles: ProfileService,
  permissionService: PermissionService,
  caseService: CaseService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canAddTeamMessageToEnquiryActionRefiner._
  import canEditEnquiryActionRefiner._
  import canViewEnquiryActionRefiner._

  private def renderMessages(enquiry: Enquiry, stateChangeForm: Form[OffsetDateTime], messageForm: Form[String])(implicit request: EnquirySpecificRequest[_]): Future[Result] = {
    ServiceResults.zip(
      service.getForRender(enquiry.id.get),
      profiles.getProfile(enquiry.universityID).map(_.value),
      service.getOwners(Set(enquiry.id.get)),
      permissionService.canViewTeamFuture(currentUser.usercode, enquiry.team),
      caseService.findFromOriginalEnquiry(enquiry.id.get)
    ).successFlatMap { case (render, profile, ownersMap, canViewTeam, linkedCases) =>
      val allUsers = render.messages.flatMap(_.message.teamMember).toSet ++ ownersMap.values.flatten ++ render.notes.map(_.teamMember).toSet
      val userLookup = userLookupService.getUsers(allUsers.toSeq).getOrElse(Map())

      val getClientLastRead: Future[ServiceResult[Option[OffsetDateTime]]] =
        profile.map { p => service.findLastViewDate(enquiry.id.get, p.usercode) }
          .getOrElse(Future.successful(Right(None)))

      getClientLastRead.successMap { clientLastRead =>
        Ok(views.html.admin.enquiry.messages(
          render.enquiry,
          profile,
          render.messages,
          render.notes,
          ownersMap.values.flatten.flatMap(userLookup.get).toSeq.sortBy { u => (u.name.last, u.name.first) },
          clientLastRead,
          userLookup,
          stateChangeForm,
          messageForm,
          canViewTeam,
          linkedCases
        ))
      }
    }
  }

  def renderMessages()(implicit request: EnquirySpecificRequest[_]): Future[Result] =
    renderMessages(
      request.enquiry,
      stateChangeForm(request.enquiry).fill(request.enquiry.version),
      messageForm
    )

  def messages(enquiryKey: IssueKey): Action[AnyContent] = CanViewEnquiryAction(enquiryKey).async { implicit request =>
    renderMessages()(request)
  }

  def redirectToMessages(enquiryKey: IssueKey): Action[AnyContent] = Action {
    Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
  }

  def addMessage(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryFile]] = CanAddTeamMessageToEnquiryAction(enquiryKey)(parse.multipartFormData).async { implicit request =>
    messageForm.bindFromRequest().fold(
      formWithErrors => {
        render.async {
          case Accepts.Json() =>
            Future.successful(API.badRequestJson(formWithErrors))
          case _ =>
            renderMessages()
        }
      },
      messageText => {
        val message = messageData(messageText, request)
        val files = UploadedFileSave.seqFromRequest(request)
        val enquiry = request.enquiry

        service.addMessage(enquiry, message, files).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, enquiry.universityID, m.created, m.teamMember, m.team)
          render {
            case Accepts.Json() =>
              val clientName = "Client"
              val teamName = message.teamMember.flatMap(usercode => userLookupService.getUser(usercode).toOption.filter(_.isFound).flatMap(_.name.full)).getOrElse(s"${request.enquiry.team.name} team")

              Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "message" -> views.html.tags.messages.message(messageData, f, clientName, teamName, f => routes.TeamEnquiryController.download(enquiryKey, f.id)).toString()
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
      render.messages.flatMap(_.files).find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  def close(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    updateState(IssueState.Closed)
  }

  def reopen(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    updateState(IssueState.Reopened)
  }

  private def updateState(newState: IssueState)(implicit request: EnquirySpecificRequest[AnyContent]): Future[Result] = {
    stateChangeForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => renderMessages(
        request.enquiry,
        formWithErrors,
        messageForm
      ),
      version =>
        service.updateState(request.enquiry, newState, version).successMap { enquiry =>
          Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiry.key.get))
            .flashing("success" -> Messages(s"flash.enquiry.$newState"))
        }
    )
  }

  private def messageData(text: String, request: EnquirySpecificRequest[_]): MessageSave =
    MessageSave(
      text = text,
      sender = MessageSender.Team,
      teamMember = request.context.user.map(_.usercode)
    )

  def reassignForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey) { implicit request =>
    Ok(views.html.admin.enquiry.reassign(request.enquiry, reassignEnquiryForm(request.enquiry).fill(ReassignEnquiryData(request.enquiry.team, request.enquiry.version, ""))))
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
          Future.successful(Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey)))
        else {
          val note = EnquiryNoteSave(
            views.txt.notes.enquiryreassign(request.enquiry.team, data.message).toString,
            request.context.user.get.usercode
          )

          service.reassign(request.enquiry, data.team, note, data.version).successMap { _ =>
            Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
              .flashing("success" -> Messages("flash.enquiry.reassigned", data.team.name))
          }
        }
    )
  }

}
