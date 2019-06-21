package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.MessagesController.MessageFormData
import controllers.admin.TeamEnquiryController._
import controllers.refiners._
import controllers.{API, BaseController, MessagesController}
import domain._
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.tabula.ProfileService
import services.{CaseService, EnquiryService, PermissionService}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
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
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.lastUpdated),
      "message" -> nonEmptyText
    )(ReassignEnquiryData.apply)(ReassignEnquiryData.unapply)
  )

  def stateChangeForm(enquiry: Enquiry): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.lastUpdated)
  ))
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

  private def renderMessages(enquiry: Enquiry, stateChangeForm: Form[OffsetDateTime], messageForm: Form[MessageFormData])(implicit request: EnquirySpecificRequest[_]): Future[Result] = {
    service.getForRender(enquiry.id).successFlatMap(enquiryRender =>
      profiles.getProfile(enquiry.client.universityID).map(_.value).successFlatMap { profile =>
        val getClientLastRead: Future[ServiceResult[Option[OffsetDateTime]]] =
          profile.map { p => service.findLastViewDate(enquiry.id, p.usercode) }
            .getOrElse(Future.successful(Right(None)))

        getClientLastRead.successFlatMap(clientLastRead =>
          render.async {
            case Accepts.Json() =>
              Future.successful(Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                "lastMessage" -> enquiryRender.messages.lastOption.map(_.message.created),
                "awaiting" -> enquiryRender.messages.lastOption.map(_.message.sender == MessageSender.Client),
                "messagesHTML" -> enquiryRender.messages.map { m =>
                  views.html.tags.messages.message(
                    m.message,
                    m.files,
                    enquiryRender.enquiry.client.safeFullName,
                    m.message.teamMember.map { member => s"${member.safeFullName}, ${m.message.team.getOrElse(enquiry.team).name}" }
                      .getOrElse(m.message.team.getOrElse(enquiry.team).name),
                    f => controllers.admin.routes.TeamEnquiryController.download(enquiry.key, f.id),
                    clientLastRead.filter(_ => m.message.sender == MessageSender.Team).map(_.isAfter(m.message.created))
                  ).toString
                }.mkString("")
              )))))
            case _ =>
              ServiceResults.zip(
                service.getOwners(Set(enquiry.id)),
                permissionService.canViewTeamFuture(currentUser.usercode, enquiry.team),
                caseService.findFromOriginalEnquiry(enquiry.id),
                service.getEnquiryHistory(enquiry.id),
                Future.successful(service.nextClientReminder(request.enquiry.id)),
              ).successMap { case (ownersMap, canViewTeam, linkedCases, enquiryHistory, nextClientReminder) =>
                Ok(views.html.admin.enquiry.messages(
                  enquiryRender.enquiry,
                  profile,
                  enquiryRender.messages,
                  enquiryRender.notes,
                  ownersMap.values.flatten.toSet,
                  clientLastRead,
                  nextClientReminder,
                  stateChangeForm,
                  messageForm,
                  canViewTeam,
                  linkedCases,
                  enquiryHistory,
                  uploadedFileControllerHelper.supportedMimeTypes
                ))
              }
          }
        )
      }
    )
  }

  def renderMessages()(implicit request: EnquirySpecificRequest[_]): Future[Result] =
    renderMessages(
      request.enquiry,
      stateChangeForm(request.enquiry).fill(request.enquiry.lastUpdated),
      MessagesController.messageForm(Some(request.lastEnquiryMessageDate)).fill(MessageFormData("", Some(request.lastEnquiryMessageDate)))
    )

  def messages(enquiryKey: IssueKey): Action[AnyContent] = CanViewEnquiryAction(enquiryKey).async { implicit request =>
    renderMessages()(request)
  }

  def redirectToMessages(enquiryKey: IssueKey): Action[AnyContent] = Action {
    Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
  }

  def addMessage(enquiryKey: IssueKey): Action[MultipartFormData[TemporaryUploadedFile]] = CanAddTeamMessageToEnquiryAction(enquiryKey)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    MessagesController.messageForm(Some(request.lastEnquiryMessageDate)).bindFromRequest().fold(
      formWithErrors => {
        render.async {
          case Accepts.Json() =>
            Future.successful(API.badRequestJson(formWithErrors))
          case _ =>
            renderMessages()
        }
      },
      messageFormData => {
        val message = messageData(messageFormData.text, request)
        val files = request.body.files.map(_.ref)
        val enquiry = request.enquiry

        service.addMessage(enquiry, message, files.map { f => (f.in, f.metadata) }).successMap { case (m, f) =>
          val messageData = MessageData(m.text, m.sender, enquiry.client.universityID, m.created, m.teamMember, m.team)
          render {
            case Accepts.Json() =>
              val clientName = "Client"
              val teamName = message.teamMember.flatMap(usercode => userLookupService.getUser(usercode).toOption.filter(_.isFound).flatMap(_.name.full)).getOrElse(request.enquiry.team.name)

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
    service.getForRender(request.enquiry.id).successFlatMap { render =>
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
        MessagesController.messageForm(Some(request.lastEnquiryMessageDate))
      ),
      version =>
        service.updateState(request.enquiry.id, newState, version).successMap { enquiry =>
          Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiry.key))
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
    Ok(views.html.admin.enquiry.reassign(request.enquiry, reassignEnquiryForm(request.enquiry).fill(ReassignEnquiryData(request.enquiry.team, request.enquiry.lastUpdated, ""))))
  }

  def reassign(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    reassignEnquiryForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.enquiry.reassign(
          request.enquiry,
          formWithErrors
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

          service.reassign(request.enquiry.id, data.team, note, data.version).successMap { _ =>
            Redirect(controllers.admin.routes.TeamEnquiryController.messages(enquiryKey))
              .flashing("success" -> Messages("flash.enquiry.reassigned", data.team.name))
          }
        }
    )
  }

  def cancelClientReminder(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    stateChangeForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => renderMessages(
        request.enquiry,
        formWithErrors,
        MessagesController.messageForm(Some(request.lastEnquiryMessageDate))
      ),
      _ => Future.successful {
        service.cancelClientReminder(request.enquiry)
        Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
          .flashing("success" -> Messages("flash.enquiry.clientReminderCancelled"))
      }
    )
  }

}
