package controllers.admin

import java.util.UUID

import controllers.BaseController
import controllers.refiners.{CanViewTeamActionRefiner, TeamSpecificRequest}
import domain.dao.CaseDao.Case
import domain.{Enquiry, MessageData}
import helpers.ServiceResults
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, Result}
import services.{CaseService, EnquiryService}
import services.tabula.ProfileService
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  enquiries: EnquiryService,
  cases: CaseService,
  profileService: ProfileService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest => {
    findEnquiriesAndCases { (userEnquiries, teamEnquiries, userCases, teamCases, owners, clients) => {
     profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val userLookup = userLookupService.getUsers(owners.values.flatten.toSeq).toOption.getOrElse(Map())
        val resolvedClients = clients.mapValues(_.flatMap(c => profiles.get(c)))
        val resolvedOwners = owners.mapValues(_.flatMap(o => userLookup.get(o).filter(_.isFound)))
        Ok(views.html.admin.teamHome(teamRequest.team, userEnquiries, teamEnquiries, userCases, teamCases, resolvedClients, resolvedOwners))
      })
    }}
  }}

  private def findEnquiriesAndCases(f: (
      Seq[(Enquiry, MessageData)],
      Seq[(Enquiry, MessageData)],
      Seq[Case],
      Seq[Case],
      Map[UUID, Set[Usercode]],
      Map[UUID, Set[UniversityID]]
    ) => Future[Result])(implicit teamRequest: TeamSpecificRequest[_]) = {
    ServiceResults.zip(
      enquiries.findEnquiriesNeedingReply(currentUser.usercode),
      enquiries.findEnquiriesNeedingReply(teamRequest.team),
      cases.listOpenCases(currentUser.usercode),
      cases.listOpenCases(teamRequest.team)
    ).map(_.right.map { case (userEnquiries, teamEnquiriesWithDupes, userCases, teamCasesWithDupes) =>
       val teamEnquiries = teamEnquiriesWithDupes.filterNot { case (teamEnquiry, _) =>
         userEnquiries.exists { case (ownerEnquiry, _) => ownerEnquiry.id.get == teamEnquiry.id.get }
       }
       val teamCases = teamCasesWithDupes.filterNot(tc => userCases.exists(_.id.get == tc.id.get))
       (userEnquiries, teamEnquiries, userCases, teamCases)
    }).successFlatMap { case (userEnquiries, teamEnquiries, userCases, teamCases) =>
      ServiceResults.zip(
        enquiries.getOwners((userEnquiries ++ teamEnquiries).flatMap { case (e, _) => e.id }.toSet),
        cases.getOwners((userCases ++ teamCases).flatMap(_.id).toSet),
        cases.getClients((userCases.flatMap(_.id) ++ teamCases.flatMap(_.id)).toSet)
      ).successFlatMap { case (enquiryOwners, caseOwners, caseClients ) =>
        val owners = enquiryOwners ++ caseOwners
        val clients = (userEnquiries ++ teamEnquiries).map{ case (e, _) => e.id.get -> Set(e.universityID) }.toMap ++ caseClients
        f(userEnquiries, teamEnquiries, userCases, teamCases, owners, clients)
      }
    }
  }

}
