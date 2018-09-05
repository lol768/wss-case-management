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

class AnyTeamActionRefiner @Inject()(
  config: Configuration,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def AnyTeamMember = new ActionFilter[AuthenticatedRequest] {
    override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      val hasPermissions: ServiceResult[Boolean] =
        if (request.context.user.isEmpty) Right(false)
        else if (request.context.user == request.context.actualUser && request.context.userHasRole(Sysadmin))
          Right(true)
        else
          permissionService.inAnyTeam(request.context.user.get.usercode)

      Future.successful {
        hasPermissions.fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.Forbidden(views.html.errors.forbidden(request.context.user.flatMap(_.name.full))))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def AnyTeamMemberRequiredAction: ActionBuilder[AuthenticatedRequest, AnyContent] =
    securityService.SigninRequiredAction andThen AnyTeamMember

}
