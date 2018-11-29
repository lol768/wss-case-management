package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanViewTeamActionRefiner @Inject()(
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private val TeamMember = PermissionsFilter[TeamSpecificRequest] { implicit request =>
    permissionService.canViewTeamFuture(request.context.user.get.usercode, request.team)
  }

  def CanViewTeamAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithTeam(teamId) andThen TeamMember

  def CanViewTeamAjaxAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAjaxAction andThen WithTeam(teamId) andThen TeamMember

}


