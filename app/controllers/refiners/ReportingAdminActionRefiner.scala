package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.ExecutionContext

@Singleton
class ReportingAdminActionRefiner @Inject()(
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private val IsReportingAdmin = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissionService.isReportingAdmin(request.context.user.get.usercode)
  }

  def ReportingAdminRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen IsReportingAdmin
}
