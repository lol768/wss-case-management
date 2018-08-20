package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner}
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.ExecutionContext

class AdminController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId) { implicit teamRequest =>
    Ok(views.html.admin.teamHome(teamRequest.team))
  }

}
