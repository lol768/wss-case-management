package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanEditCaseActionRefiner @Inject()(
  config: Configuration,
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitCaseService: CaseService = caseService

  private def CanEditCase[A] = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canEditCase(request.context.user.get.usercode, request.`case`.id.get)
  }

  def CanEditCaseAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey) andThen CanEditCase

}
