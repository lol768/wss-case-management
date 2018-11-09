package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CaseMessageActionFilters @Inject()(
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit
  ec: ExecutionContext,
  caseService: CaseService
) extends ImplicitRequestContext {

  // gasp, two filters in one class

  private val CanPostAsTeam = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canAddTeamMessageToCase(request.context.user.get, request.`case`.id)
  }

  private val CanPostAsClient = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canAddClientMessageToCase(request.context.user.get, request.`case`.id)
  }

  private def CaseAction(caseKey: IssueKey) =
    securityService.SigninRequiredAction andThen WithCase(caseKey)

  def CanPostAsTeamAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    CaseAction(caseKey) andThen CanPostAsTeam

  def CanPostAsClientAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    CaseAction(caseKey) andThen CanPostAsTeam

}
