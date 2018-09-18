package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.refiners.{CanViewTeamActionRefiner, TeamSpecificRequest}
import domain.dao.CaseDao.Case
import domain.{Enquiry, MessageData}
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.tabula.ProfileService
import services.{CaseService, EnquiryService}
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  enquiries: EnquiryService,
  cases: CaseService,
  profileService: ProfileService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    findEnquiriesAndCases { (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, clients) => {
     profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val resolvedClients = clients.mapValues(_.flatMap(c => profiles.get(c)))
        Ok(views.html.admin.teamHome(teamRequest.team, enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, resolvedClients))
      })
    }}
  }

  private def findEnquiriesAndCases(f: (
      Seq[(Enquiry, MessageData)],
      Seq[(Enquiry, MessageData)],
      Int,
      Seq[(Case, OffsetDateTime)],
      Int,
      Map[UUID, Set[UniversityID]]
    ) => Future[Result])(implicit teamRequest: TeamSpecificRequest[_]) = {
    ServiceResults.zip(
      enquiries.findEnquiriesNeedingReply(teamRequest.team),
      enquiries.findEnquiriesAwaitingClient(teamRequest.team),
      enquiries.countClosedEnquiries(teamRequest.team),
      cases.listOpenCases(teamRequest.team),
      cases.countClosedCases(teamRequest.team)
    ).successFlatMap { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases) =>
      cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { caseClients =>
        val clients = (enquiriesNeedingReply ++ enquiriesAwaitingClient).map{ case (e, _) => e.id.get -> Set(e.universityID) }.toMap ++ caseClients
        f(enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, clients)
      }
    }
  }

  def closedEnquiries(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    enquiries.findClosedEnquiries(teamRequest.team).successFlatMap { enquiries =>
      val clients = enquiries.map { case (e, _) => e.id.get -> Set(e.universityID) }.toMap

      profileService.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
        val resolvedClients = clients.mapValues(_.flatMap(c => profiles.get(c)))
        Ok(views.html.admin.teamClosedEnquiries(teamRequest.team, enquiries, resolvedClients))
      }
    }
  }

  def closedCases(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    cases.listClosedCases(teamRequest.team).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { clients =>
        profileService.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
          val resolvedClients = clients.mapValues(_.flatMap(c => profiles.get(c)))
          Ok(views.html.admin.teamClosedCases(teamRequest.team, closedCases, resolvedClients))
        }
      }
    }
  }

}
