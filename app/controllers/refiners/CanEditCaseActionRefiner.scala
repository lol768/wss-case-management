package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CanEditCaseActionRefiner @Inject()(
  config: Configuration,
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitCaseService: CaseService = caseService

  private def CanEditCase = new ActionFilter[CaseSpecificRequest] {
    override protected def filter[A](request: CaseSpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      permissionService.canEditCase(request.context.user.get.usercode, request.`case`.id.get).map(_.fold(
        errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
        canEditCase =>
          if (canEditCase) None
          else Some(Results.NotFound(views.html.errors.notFound()))
      ))
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def CanEditCaseAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey) andThen CanEditCase

}
