package controllers.refiners

import javax.inject.Inject
import play.api.Configuration
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.ExecutionContext

class AnyTeamActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def AnyTeamMember[A] = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissionService.inAnyTeam(request.context.user.get.usercode)
  }

  def AnyTeamMemberRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen AnyTeamMember

}
