package controllers

import domain.{Team, Teams}
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc._
import services.SecurityService
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TeamSpecificActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService
) extends ImplicitRequestContext {

  private def WithTeam(teamId: String) = new ActionRefiner[AuthenticatedRequest, TeamSpecificRequest] {

    override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, TeamSpecificRequest[A]]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request
      Future.successful {
        Try(Teams.fromId(teamId)).toOption.map(t => Right(new TeamSpecificRequest[A](t, request)))
          .getOrElse(Left(Results.NotFound(views.html.errors.notFound())))
      }
    }

    override protected def executionContext: ExecutionContext = ExecutionContext.global
  }

  def TeamSpecificAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninAwareAction andThen WithTeam(teamId)

  def TeamSpecificSignInRequiredAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithTeam(teamId)

}

class TeamSpecificRequest[A](val team: Team, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
