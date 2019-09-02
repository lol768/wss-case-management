package controllers.admin

import java.io.InputStream
import java.util.UUID

import com.google.common.io.ByteSource
import controllers.refiners.{CanViewCaseActionRefiner, CaseMessageActionFilters}
import controllers.{API, BaseController, MessagesController}
import domain.AuditEvent._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.{AuditService, CaseService, MessageSnippetService}
import warwick.core.helpers.JavaTime.offsetDateTimeISOWrites
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.fileuploads.{UploadedFileControllerHelper, UploadedFileSave}
import warwick.objectstore.ObjectStorageService
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseMessageController @Inject() (
  actions: CaseMessageActionFilters,
  canViewCase: CanViewCaseActionRefiner,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  userLookupService: UserLookupService,
  auditService: AuditService,
  messageSnippets: MessageSnippetService,
  objectStorageService: ObjectStorageService,
) (implicit
  executionContext: ExecutionContext,
  caseService: CaseService,
) extends BaseController {

  import actions._
  import canViewCase.CanViewCaseAction

  def messages(caseKey: IssueKey, universityID: UniversityID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    caseService.getCaseMessages(caseRequest.`case`.id).successMap(messages =>
      messagesResult(caseRequest.`case`, universityID, messages)
    )
  }

  def messagesResult(`case`: Case, universityID: UniversityID, messages: CaseMessages): Result = {
    Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
      "lastMessage" -> messages.byClient(universityID).lastOption.map(_.message.created),
      "lastMessageRelative" -> messages.byClient(universityID).lastOption.map(_.message.created).map(JavaTime.Relative.apply(_)),
      "threadTitle" -> (if (messages.byClient(universityID).isEmpty) "No messages" else views.html.tags.p(messages.byClient(universityID).length, "message")()),
      "awaiting" -> messages.byClient(universityID).lastOption.map(_.message.sender == MessageSender.Client),
      "messagesHTML" -> messages.byClient(universityID).map { m =>
        views.html.tags.messages.message(
          m.message,
          m.files,
          "Client",
          m.message.teamMember.map { member => s"${member.safeFullName}, ${m.message.team.getOrElse(`case`.team).name}" }
            .getOrElse(m.message.team.getOrElse(`case`.team).name),
          f => controllers.admin.routes.CaseMessageController.download(`case`.key, f.id),
          // TODO last view date for cases
          None
        ).toString
      }.mkString("")
    ))))
  }

  def addMessage(caseKey: IssueKey, universityID: UniversityID): Action[MultipartFormData[TemporaryUploadedFile]] = CanPostAsTeamAction(caseKey)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    caseService.getLastUpdatedMessageDates(caseKey).successFlatMap { lastUpdatedDates =>
      val lastUpdatedDateForClient = lastUpdatedDates.get(universityID)
      MessagesController.messageForm(lastUpdatedDateForClient).bindFromRequest().fold(
        formWithErrors => {
          render.async {
            case Accepts.Json() =>
              Future.successful(API.badRequestJson(formWithErrors))
            case _ =>
              Future.successful(Redirect(controllers.admin.routes.CaseController.view(caseKey)).flashing("error" -> formWithErrors.errors.map(_.format).mkString(", ")))
          }
        },
        messageFormData => {
          val message = messageSave(messageFormData.text, currentUser().usercode)
          val files = request.body.files.map(_.ref)

          // Files from message snippets
          val fetchFilesToCopy: Future[ServiceResult[Seq[(ByteSource, UploadedFileSave)]]] =
            if (messageFormData.filesToCopy.isEmpty) Future.successful(ServiceResults.success(Nil))
            else messageSnippets.list().successMapTo { snippets =>
              snippets.flatMap(_.files).filter(f => messageFormData.filesToCopy.contains(f.id)).map { file =>
                val source = new ByteSource {
                  override def openStream(): InputStream = objectStorageService.fetch(file.id.toString).orNull
                }

                (source, UploadedFileSave(file.fileName, file.contentLength, file.contentType))
              }
            }

          fetchFilesToCopy.successFlatMap { copiedFiles =>
            caseService.addMessage(request.`case`, universityID, message, files.map(f => (f.in, f.metadata)) ++ copiedFiles).successFlatMap { case (m, f) =>
              val messageData = MessageData(m.text, m.sender, universityID, m.created, m.teamMember, m.team)
              render.async {
                case Accepts.Json() =>
                  caseService.getCaseMessages(request.`case`.id).successMap { messages =>
                    val teamName = messageData.teamMember.map { member => s"${member.safeFullName}, ${messageData.team.getOrElse(request.`case`.team).name}" }
                      .getOrElse(messageData.team.getOrElse(request.`case`.team).name)

                    Ok(Json.toJson(API.Success[JsObject](data = Json.obj(
                      "lastMessage" -> messageData.created,
                      "lastMessageRelative" -> JavaTime.Relative(messageData.created),
                      "threadTitle" -> (if (messages.byClient(universityID).isEmpty) "No messages" else views.html.tags.p(messages.byClient(universityID).length, "message")()),
                      "message" -> views.html.tags.messages.message(
                        messageData,
                        f,
                        "Client",
                        teamName,
                        f => routes.CaseMessageController.download(caseKey, f.id)
                      ).toString()
                    ))))
                  }
                case _ =>
                  Future.successful(Redirect(controllers.admin.routes.CaseController.view(request.`case`.key).withFragment(s"thread-heading-${universityID.string}")))
              }
            }
          }
        }
      )
    }
  }

  def download(caseKey: IssueKey, fileId: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit request =>
    caseService.getCaseMessages(request.`case`.id).successFlatMapTo(messages =>
      messages.data.flatMap(_.files).find(_.id == fileId)
        .map(f => auditService.audit(Operation.Case.DownloadDocument, fileId.toString, Target.CaseDocument, Json.obj()) {
          uploadedFileControllerHelper.serveFile(f).map(Right.apply)
        })
        .getOrElse(Future.successful(Right(NotFound(views.html.errors.notFound()))))
    ).successMap(r => r)
  }

  private def messageSave(text: String, teamMember: Usercode) = MessageSave(
    text = text,
    sender = MessageSender.Team,
    teamMember = Some(teamMember)
  )

}
