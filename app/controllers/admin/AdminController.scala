package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.refiners.{CanViewTeamActionRefiner, TeamSpecificRequest}
import domain.dao.CaseDao.Case
import domain.{AppointmentRender, Enquiry, MessageData, Teams}
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.tabula.ProfileService
import services.{AppointmentService, CaseService, ClientSummaryService, EnquiryService}
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  enquiries: EnquiryService,
  cases: CaseService,
  clientSummaryService: ClientSummaryService,
  profileService: ProfileService,
  userLookupService: UserLookupService,
  appointments: AppointmentService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    findEnquiriesAndCasesAndAppointments { (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, confirmedAppointments, attendedAppointments, cancelledAppointments, clients) => {
      profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        val usercodes = (declinedAppointments ++ provisionalAppointments ++ appointmentsNeedingOutcome).map(_.appointment.teamMember).toSet
        val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

        Ok(views.html.admin.teamHome(teamRequest.team, enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, confirmedAppointments, attendedAppointments, cancelledAppointments, resolvedClients, userLookup))
      })
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
      Seq[AppointmentRender], // In the past, Confirmed or Provisional
      Int, // Confirmed, in future
      Int, // Attended
      Int, // Cancelled
      Map[UUID, Set[UniversityID]]
    ) => Future[Result])(implicit teamRequest: TeamSpecificRequest[_]) = {
    ServiceResults.zip(
      enquiries.findEnquiriesNeedingReply(teamRequest.team),
      enquiries.findEnquiriesAwaitingClient(teamRequest.team),
      enquiries.countClosedEnquiries(teamRequest.team),
      cases.listOpenCases(teamRequest.team),
      cases.countClosedCases(teamRequest.team),
      appointments.findDeclinedAppointments(teamRequest.team),
      appointments.findProvisionalAppointments(teamRequest.team),
      appointments.findAppointmentsNeedingOutcome(teamRequest.team),
      appointments.countConfirmedAppointments(teamRequest.team),
      appointments.countAttendedAppointments(teamRequest.team),
      appointments.countCancelledAppointments(teamRequest.team),
    ).successFlatMap { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, confirmedAppointments, attendedAppointments, cancelledAppointments) =>
      cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { caseClients =>
        val clients = (enquiriesNeedingReply ++ enquiriesAwaitingClient).map{ case (e, _) => e.id.get -> Set(e.universityID) }.toMap ++ caseClients ++ (declinedAppointments ++ provisionalAppointments ++ appointmentsNeedingOutcome).map { a => a.appointment.id -> a.clients.map(_.universityID) }
        f(enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, confirmedAppointments, attendedAppointments, cancelledAppointments, clients)
      }
    }
  }

  def closedEnquiries(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    enquiries.findClosedEnquiries(teamRequest.team).successFlatMap { enquiries =>
      val clients = enquiries.map { case (e, _) => e.id.get -> Set(e.universityID) }.toMap

      profileService.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        Ok(views.html.admin.closedEnquiries(enquiries, resolvedClients))
      }
    }
  }

  def closedCases(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    cases.listClosedCases(teamRequest.team).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { clients =>
        profileService.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
          val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
          Ok(views.html.admin.closedCases(closedCases, resolvedClients))
        }
      }
    }
  }

  def atRiskClients(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    clientSummaryService.findAtRisk(teamRequest.team == Teams.MentalHealth).successMap(clients =>
      Ok(views.html.admin.atRiskClients(clients, teamRequest.team == Teams.MentalHealth))
    )
  }

  def confirmedAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findConfirmedAppointments(teamRequest.team).successFlatMap { appointments =>
      val clients = appointments.map { a => a.appointment.id -> a.clients.map(_.universityID) }.toMap

      profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        val usercodes = appointments.map(_.appointment.teamMember).toSet
        val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

        Ok(views.html.admin.confirmedAppointments(appointments, resolvedClients, Some(userLookup)))
      })
    }
  }

  def attendedAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findAttendedAppointments(teamRequest.team).successFlatMap { appointments =>
      val clients = appointments.map { a => a.appointment.id -> a.clients.map(_.universityID) }.toMap

      profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        val usercodes = appointments.map(_.appointment.teamMember).toSet
        val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

        Ok(views.html.admin.attendedAppointments(appointments, resolvedClients, Some(userLookup)))
      })
    }
  }

  def cancelledAppointments(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments.findCancelledAppointments(teamRequest.team).successFlatMap { appointments =>
      val clients = appointments.map { a => a.appointment.id -> a.clients.map(_.universityID) }.toMap

      profileService.getProfiles(clients.values.flatten.toSet).successMap(profiles => {
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        val usercodes = appointments.map(_.appointment.teamMember).toSet
        val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

        Ok(views.html.admin.cancelledAppointments(appointments, resolvedClients, Some(userLookup)))
      })
    }
  }

}
