package controllers.admin

import domain.IssueKey
import domain.dao.CaseDao.Case
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{CaseService, PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseSpecificActionRefiner @Inject()(
  config: Configuration,
  cases: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def WithCase(caseKey: IssueKey) = new ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] {
    override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, CaseSpecificRequest[A]]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      cases.find(caseKey).map {
        case Right(c) =>
          Right(new CaseSpecificRequest[A](c, request))

        case _ =>
          Left(Results.NotFound(views.html.errors.notFound()))
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def TeamMember = new ActionFilter[CaseSpecificRequest] {
    override protected def filter[A](request: CaseSpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      Future.successful {
        permissionService.inTeam(request.context.user.get.usercode, request.`case`.clientCase.team).fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.NotFound(views.html.errors.notFound()))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def CaseSpecificSignInRequiredAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithCase(caseKey)

  // FIXME to be solved as part of permissions refactor
  def CaseSpecificTeamMemberAction(caseKey: IssueKey): ActionBuilder[CaseSpecificRequest, AnyContent] =
    CaseSpecificSignInRequiredAction(caseKey) andThen TeamMember

}

class CaseSpecificRequest[A](val `case`: Case.FullyJoined, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
