package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.ExecutionContext

@Singleton
class AnyTeamActionRefiner @Inject()(
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private val AnyTeamMember = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissionService.inAnyTeam(request.context.user.get.usercode)
  }

  def AnyTeamMemberRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen AnyTeamMember

  def AnyTeamMemberRequiredAjaxAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAjaxAction andThen AnyTeamMember

}
