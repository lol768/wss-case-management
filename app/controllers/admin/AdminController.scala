package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner}
import helpers.ServiceResults
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

  def teamHome(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId).async { implicit teamRequest =>
    enquiries.findEnquiriesNeedingReply.successFlatMap(needsAction => {
      ServiceResults.futureSequence(needsAction.map { case (enquiry, message) =>
        profileService.getProfile(enquiry.universityID).map(_.value.right.map(profile => (enquiry, message, profile)))
      }).successMap(enquiries => {
        val currentTeamFirst = enquiries.filter(_._1.team == teamRequest.team) ++ enquiries.filterNot(_._1.team == teamRequest.team)
        Ok(views.html.admin.teamHome(teamRequest.team, currentTeamFirst))
      })
    })
  }

}
