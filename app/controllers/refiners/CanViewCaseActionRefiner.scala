package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanViewCaseActionRefiner @Inject()(
  config: Configuration,
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  implicit val implicitCaseService: CaseService = caseService

  private def CanViewCase[A] = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canViewCase(request.context.user.get.usercode)
  }

  def CanViewCaseAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey) andThen CanViewCase

}
