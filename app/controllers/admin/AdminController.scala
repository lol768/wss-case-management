package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.refiners.{CanViewTeamActionRefiner, TeamSpecificRequest}
import controllers.{API, BaseController}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.CaseDao.Case
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Result}
import services.{AppointmentService, CaseService, ClientSummaryService, EnquiryService}
import warwick.sso.UserLookupService

import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  enquiries: EnquiryService,
  cases: CaseService,
  clientSummaryService: ClientSummaryService,
  userLookupService: UserLookupService,
  appointments: AppointmentService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    findEnquiriesAndCasesAndAppointments { (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, cancelledAppointments, caseClients) => {
      Ok(views.html.admin.teamHome(teamRequest.team, enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, cancelledAppointments, caseClients))
    }}
  }

  private def findEnquiriesAndCasesAndAppointments(f: (
      Seq[(Enquiry, MessageData)],
      Seq[(Enquiry, MessageData)],
      Int,
      Seq[(Case, OffsetDateTime)],
      Int,
      Int, // Cancelled
      Map[UUID, Set[Client]]
    ) => Result)(implicit teamRequest: TeamSpecificRequest[_]) = {
    ServiceResults.zip(
      enquiries.findEnquiriesNeedingReply(teamRequest.team),
      enquiries.findEnquiriesAwaitingClient(teamRequest.team),
      enquiries.countClosedEnquiries(teamRequest.team),
      cases.listOpenCases(teamRequest.team),
      cases.countClosedCases(teamRequest.team),
      appointments.countCancelledAppointments(teamRequest.team),
    ).successFlatMap { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, cancelledAppointments) =>
      cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successMap { caseClients =>
        f(enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, cancelledAppointments, caseClients)
      }
    }
  }

  def closedEnquiries(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    enquiries.findClosedEnquiries(teamRequest.team).successMap { enquiries =>
      Ok(views.html.admin.closedEnquiries(enquiries))
    }
  }

  def closedCases(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    cases.listClosedCases(teamRequest.team).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successMap { clients =>
        Ok(views.html.admin.closedCases(closedCases, clients))
      }
    }
  }

  def atRiskClients(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    clientSummaryService.findAtRisk(teamRequest.team == Teams.MentalHealth).successMap(clients =>
      Ok(views.html.admin.atRiskClients(clients, teamRequest.team == Teams.MentalHealth))
    )
  }

  def cancelledAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findCancelledAppointments(teamRequest.team).successMap { appointments =>
      Ok(views.html.admin.cancelledAppointments(appointments))
    }
  }

  def appointments(teamId: String, start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = start.map(_.toLocalDate),
      startBefore = end.map(_.toLocalDate),
      team = Some(teamRequest.team),
      states = Set(AppointmentState.Provisional, AppointmentState.Accepted, AppointmentState.Attended),
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
