package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanViewCaseActionRefiner @Inject()(
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  implicit val implicitCaseService: CaseService = caseService

  private val CanViewCase = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canViewCase(request.context.user.get.usercode, request.`case`.id.get)
  }

  def CanViewCaseAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey) andThen CanViewCase

  def CanViewCaseAction(keyOrId: String): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(keyOrId) andThen CanViewCase

}
