package controllers.refiners

import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc._
import services.{PermissionService, SecurityService}
import system.ImplicitRequestContext
import system.Roles.Sysadmin
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

class CanViewTeamActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def TeamMember = new ActionFilter[TeamSpecificRequest] {
    override protected def filter[A](request: TeamSpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      val hasPermissions: ServiceResult[Boolean] =
        if (request.context.user.isEmpty) Right(false)
        else if (request.context.user == request.context.actualUser && request.context.userHasRole(Sysadmin))
          Right(true)
        else
          permissionService.canViewTeam(request.context.user.get.usercode, request.team)

      Future.successful {
        hasPermissions.fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.NotFound(views.html.errors.notFound()))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def CanViewTeamAction(teamId: String): ActionBuilder[TeamSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithTeam(teamId) andThen TeamMember

}


