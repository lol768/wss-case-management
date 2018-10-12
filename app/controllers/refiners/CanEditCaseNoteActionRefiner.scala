package controllers.refiners

import java.util.UUID

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanEditCaseNoteActionRefiner @Inject()(
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitCaseService: CaseService = caseService

  private val CanEditCaseNote = PermissionsFilter[CaseNoteSpecificRequest] { implicit request =>
    permissionService.canEditCaseNote(request.context.user.get.usercode, request.caseNote.id)
  }

  def CanEditCaseNoteAction(id: UUID): ActionBuilder[CaseNoteSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCaseNote(id) andThen CanEditCaseNote

}
