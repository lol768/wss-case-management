package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner, TeamSpecificRequest}
import domain.{Enquiry, MessageData}
import helpers.ServiceResults
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, Result}
import services.EnquiryService
import services.tabula.ProfileService
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  enquiries: EnquiryService,
  profileService: ProfileService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId).async { implicit teamRequest => {
    findEnquiriesNeedingReply { (needsActionOwner, needsActionTeam) =>
      ServiceResults.zip(
        profileService.getProfiles((needsActionOwner ++ needsActionTeam).map { case (e, _) => e.universityID }.toSet),
        enquiries.getOwners((needsActionOwner ++ needsActionTeam).map { case (e, _) => e.id.get }.toSet)
      ).successMap { case (profiles, owners) =>
        val userLookup = userLookupService.getUsers(owners.values.flatten.toSeq).toOption.getOrElse(Map())
        val resolvedOwners = owners.mapValues(_.flatMap(o => userLookup.get(o).filter(_.isFound)))
        Ok(views.html.admin.teamHome(teamRequest.team, needsActionOwner, needsActionTeam, profiles, resolvedOwners))
      }
    }
  }}

  private def findEnquiriesNeedingReply(f: (Seq[(Enquiry, MessageData)], Seq[(Enquiry, MessageData)]) => Future[Result])(implicit teamRequest: TeamSpecificRequest[_]) =
    enquiries.findEnquiriesNeedingReply(currentUser.usercode).successFlatMap(needsActionOwner =>
      enquiries.findEnquiriesNeedingReply(teamRequest.team)
        .map(_.map(_.filterNot { case (teamEnquiry, _) => needsActionOwner.exists { case (ownerEnquiry, _) => ownerEnquiry.id.get == teamEnquiry.id.get }}))
        .successFlatMap(needsActionTeam => f(needsActionOwner, needsActionTeam))
    )

}
