package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.refiners.{CanViewTeamActionRefiner, TeamSpecificRequest}
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
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
    findEnquiriesAndCasesAndAppointments { (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, acceptedAppointments, attendedAppointments, cancelledAppointments, caseClients) => {
      val usercodes = (declinedAppointments ++ provisionalAppointments ++ appointmentsNeedingOutcome).map(_.appointment.teamMember).toSet
      val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

      Ok(views.html.admin.teamHome(teamRequest.team, enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, acceptedAppointments, attendedAppointments, cancelledAppointments, caseClients, userLookup))
    }}
  }

  private def findEnquiriesAndCasesAndAppointments(f: (
      Seq[(Enquiry, MessageData)],
      Seq[(Enquiry, MessageData)],
      Int,
      Seq[(Case, OffsetDateTime)],
      Int,
      Seq[AppointmentRender], // Needing re-schedule
      Seq[AppointmentRender], // Awaiting reply from client (in future)
      Seq[AppointmentRender], // In the past, Accepted or Provisional
      Int, // Accepted, in future
      Int, // Attended
      Int, // Cancelled
      Map[UUID, Set[Client]]
    ) => Result)(implicit teamRequest: TeamSpecificRequest[_]) = {
    ServiceResults.zip(
      enquiries.findEnquiriesNeedingReply(teamRequest.team),
      enquiries.findEnquiriesAwaitingClient(teamRequest.team),
      enquiries.countClosedEnquiries(teamRequest.team),
      cases.listOpenCases(teamRequest.team),
      cases.countClosedCases(teamRequest.team),
      appointments.findDeclinedAppointments(teamRequest.team),
      appointments.findProvisionalAppointments(teamRequest.team),
      appointments.findAppointmentsNeedingOutcome(teamRequest.team),
      appointments.countAcceptedAppointments(teamRequest.team),
      appointments.countAttendedAppointments(teamRequest.team),
      appointments.countCancelledAppointments(teamRequest.team),
    ).successFlatMap { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, acceptedAppointments, attendedAppointments, cancelledAppointments) =>
      cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successMap { caseClients =>
        f(enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, acceptedAppointments, attendedAppointments, cancelledAppointments, caseClients)
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

  def acceptedAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findAcceptedAppointments(teamRequest.team).successMap { appointments =>
      val usercodes = appointments.map(_.appointment.teamMember).toSet
      val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

      Ok(views.html.admin.acceptedAppointments(appointments, Some(userLookup)))
    }
  }

  def attendedAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findAttendedAppointments(teamRequest.team).successMap { appointments =>
      val usercodes = appointments.map(_.appointment.teamMember).toSet
      val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

      Ok(views.html.admin.attendedAppointments(appointments, Some(userLookup)))
    }
  }

  def cancelledAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findCancelledAppointments(teamRequest.team).successMap { appointments =>
      val usercodes = appointments.map(_.appointment.teamMember).toSet
      val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

      Ok(views.html.admin.cancelledAppointments(appointments, Some(userLookup)))
    }
  }

}
