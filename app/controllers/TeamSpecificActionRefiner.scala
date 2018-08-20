package controllers

import domain.{Team, Teams}
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TeamSpecificActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def WithTeam(teamId: String) = new ActionRefiner[AuthenticatedRequest, TeamSpecificRequest] {

    override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, TeamSpecificRequest[A]]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request
      Future.successful {
        Try(Teams.fromId(teamId)).toOption.map(t => Right(new TeamSpecificRequest[A](t, request)))
          .getOrElse(Left(Results.NotFound(views.html.errors.notFound())))
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def TeamMember = new ActionFilter[TeamSpecificRequest] {
    override protected def filter[A](request: TeamSpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      Future.successful {
        permissionService.inTeam(request.context.user.get.usercode, request.team).fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.NotFound(views.html.errors.notFound()))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def AnyTeamMember = new ActionFilter[AuthenticatedRequest] {
    override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      Future.successful {
        permissionService.inAnyTeam(request.context.user.get.usercode).fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.Forbidden(views.html.errors.forbidden(request.context.user.flatMap(_.name.full))))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def TeamSpecificAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninAwareAction andThen WithTeam(teamId)

  def TeamSpecificSignInRequiredAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithTeam(teamId)

  def TeamSpecificMemberRequiredAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    TeamSpecificSignInRequiredAction(teamId) andThen TeamMember

  def AnyTeamMemberRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen AnyTeamMember

}

class TeamSpecificRequest[A](val team: Team, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
