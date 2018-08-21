package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner}
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.EnquiryService
import services.tabula.ProfileService
import scala.concurrent.ExecutionContext

class AdminController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  enquiries: EnquiryService,
  profileService: ProfileService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId).async { implicit teamRequest => {
    enquiries.findEnquiriesNeedingReply(teamRequest.team).successFlatMap(needsAction => {
      profileService.getProfiles(needsAction.map { case (e, _) => e.universityID }.toSet).successMap(profiles => {
        Ok(views.html.admin.teamHome(teamRequest.team, needsAction, profiles))
      })
    })
  }}

}
