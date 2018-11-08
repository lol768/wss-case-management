package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanEditCaseActionRefiner @Inject()(
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitCaseService: CaseService = caseService

  private val CanEditCase = PermissionsFilter[CaseSpecificRequest] { implicit request =>
    permissionService.canEditCase(request.context.user.get.usercode, request.`case`.id)
  }

  def CanEditCaseAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey) andThen CanEditCase

}
