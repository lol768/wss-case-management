package controllers.refiners

import javax.inject.Inject
import play.api.Configuration
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

class CanViewTeamActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def TeamMember[A] = PermissionsFilter[TeamSpecificRequest] { implicit request =>
    permissionService.canViewTeamFuture(request.context.user.get.usercode, request.team)
  }

  def CanViewTeamAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithTeam(teamId) andThen TeamMember

}


