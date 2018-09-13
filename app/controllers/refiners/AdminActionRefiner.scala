package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.ExecutionContext

@Singleton
class AdminActionRefiner @Inject()(
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private val IsAdmin = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissionService.isAdmin(request.context.user.get.usercode)
  }

  def AdminRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen IsAdmin

}
