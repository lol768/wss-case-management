package controllers.admin

import java.time.OffsetDateTime

import controllers.refiners.CanViewTeamActionRefiner
import controllers.{API, BaseController}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}
import services._
import warwick.sso.UserLookupService

import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  enquiryService: EnquiryService,
  caseService: CaseService,
  clientSummaryService: ClientSummaryService,
  userLookupService: UserLookupService,
  appointments: AppointmentService,
  userPreferences: UserPreferencesService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    userPreferences.get(currentUser().usercode).successMap { prefs =>
      Ok(views.html.admin.teamHome(teamRequest.team, prefs))
    }
  }

  def enquiries(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(teamRequest.team),
      enquiryService.findEnquiriesNeedingReply(teamRequest.team, Pagination.firstPage()),
      enquiryService.countEnquiriesAwaitingClient(teamRequest.team),
      enquiryService.findEnquiriesAwaitingClient(teamRequest.team, Pagination.firstPage()),
      enquiryService.countClosedEnquiries(teamRequest.team)
    ).successMap { case (requiringActionCount, requiringAction, awaitingClientCount, awaitingClient, closedEnquiries) =>
      Ok(views.html.admin.enquiriesTab(
        requiringAction,
        Pagination(requiringActionCount, 0, controllers.admin.routes.AdminController.enquiriesNeedingReply(teamRequest.team.id)),
        awaitingClient,
        Pagination(awaitingClientCount, 0, controllers.admin.routes.AdminController.enquiriesAwaitingClient(teamRequest.team.id)),
        closedEnquiries,
        controllers.admin.routes.AdminController.closedEnquiries(teamId),
        "team",
        teamId,
        s" assigned to ${teamRequest.team.name}"
      ))
    }
  }

  def enquiriesNeedingReply(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(teamRequest.team),
      enquiryService.findEnquiriesNeedingReply(teamRequest.team, Pagination.asPage(page))
    ).successMap { case (requiringActionCount, requiringAction) =>
      val pagination = Pagination(requiringActionCount, page, controllers.admin.routes.AdminController.enquiriesNeedingReply(teamRequest.team.id))
      Ok(views.html.admin.enquiriesNeedingReply(requiringAction, pagination))
    }
  }

  def enquiriesAwaitingClient(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      enquiryService.countEnquiriesAwaitingClient(teamRequest.team),
      enquiryService.findEnquiriesAwaitingClient(teamRequest.team, Pagination.asPage(page))
    ).successMap { case (awaitingClientCount, awaitingClient) =>
      val pagination = Pagination(awaitingClientCount, page, controllers.admin.routes.AdminController.enquiriesAwaitingClient(teamRequest.team.id))
      Ok(views.html.admin.enquiriesAwaitingClient(awaitingClient, pagination))
    }
  }

  def closedEnquiries(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      enquiryService.countClosedEnquiries(teamRequest.team),
      enquiryService.findClosedEnquiries(teamRequest.team, Pagination.asPage(page))
    ).successMap { case (closed, closedEnquiries) =>
      val pagination = Pagination(closed, page, controllers.admin.routes.AdminController.closedEnquiries(teamRequest.team.id))
      Ok(views.html.admin.closedEnquiries(closedEnquiries, pagination))
    }
  }

  def cases(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      caseService.countOpenCases(teamRequest.team),
      caseService.listOpenCases(teamRequest.team, Pagination.firstPage()),
      caseService.countClosedCases(teamRequest.team)
    ).successFlatMap { case (open, openCases, closedCases) =>
      val pagination = Pagination(open, 0, controllers.admin.routes.AdminController.openCases(teamRequest.team.id))
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { caseClients =>
        Ok(views.html.admin.casesTab(
          openCases,
          pagination,
          closedCases,
          caseClients,
          controllers.admin.routes.CaseController.createForm(teamId),
          controllers.admin.routes.AdminController.closedCases(teamId),
          "team",
          teamId,
          s" assigned to ${teamRequest.team.name}"
        ))
      }
    }
  }

  def openCases(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      caseService.countOpenCases(teamRequest.team),
      caseService.listOpenCases(teamRequest.team, Pagination.asPage(page))
    ).successFlatMap { case (open, openCases) =>
      val pagination = Pagination(open, page, controllers.admin.routes.AdminController.openCases(teamRequest.team.id))
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.openCases(openCases, clients, pagination))
      }
    }
  }

  def closedCases(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    ServiceResults.zip(
      caseService.countClosedCases(teamRequest.team),
      caseService.listClosedCases(teamRequest.team, Pagination.asPage(page))
    ).successFlatMap { case (closed, closedCases) =>
      val pagination = Pagination(closed, page, controllers.admin.routes.AdminController.closedCases(teamRequest.team.id))
      caseService.getClients(closedCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.closedCases(closedCases, clients, pagination))
      }
    }
  }

  def atRiskClients(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    clientSummaryService.findAtRisk(teamRequest.team == Teams.MentalHealth).successMap(clients =>
      Ok(views.html.admin.atRiskClients(clients, teamRequest.team == Teams.MentalHealth))
    )
  }

  def appointments(teamId: String, start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = start.map(_.toLocalDate),
      startBefore = end.map(_.toLocalDate),
      team = Some(teamRequest.team),
    )).successMap { appointments =>
      render {
        case Accepts.Json() =>
          Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(appointments)(Writes.seq(AppointmentRender.writer)))))
        case _ =>
          Redirect(controllers.admin.routes.AdminController.teamHome(teamId).withFragment("appointments"))
      }
    }
  }

}
