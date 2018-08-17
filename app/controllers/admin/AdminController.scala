package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner}
import domain.Team
import javax.inject.Inject
import play.api.mvc.{RequestHeader, Result}
import services.PermissionService
import views.html.errors.notFound
import warwick.sso.AuthenticatedRequest

import scala.concurrent.ExecutionContext

class AdminController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  permissions: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  def teamHome(teamId: String) = TeamSpecificSignInRequiredAction(teamId) { implicit teamRequest =>
    ifInTeam(teamRequest.team) {
      Ok(views.html.admin.teamHome(teamRequest.team))
    }
  }

  private def ifInTeam(team: Team)(fn: => Result)(implicit r: AuthenticatedRequest[_]): Result =
    permissions.inTeam(currentUser().usercode, team).fold(
      showErrors,
      inTeam =>
        if (inTeam) {
          fn
        } else {
          NotFound(views.html.errors.notFound())
        }
    )

}
