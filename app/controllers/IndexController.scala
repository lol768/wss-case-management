package controllers

import java.time.OffsetDateTime
import java.util.UUID

import controllers.IndexController.{ClientInformation, TeamMemberInformation}
import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.CaseDao.Case
import helpers.{JavaTime, ServiceResults}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services._
import services.tabula.ProfileService
import warwick.sso.{AuthenticatedRequest, UniversityID, User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object IndexController {
  case class ClientInformation(
    issues: Seq[IssueRender],
    registration: Option[Registration],
    appointments: Seq[AppointmentRender],
    userLookup: Map[Usercode, User]
  )

  case class TeamMemberInformation(
    teams: Seq[Team],
    enquiriesRequiringAction: Seq[(Enquiry, MessageData)],
    enquiriesAwaitingClient: Seq[(Enquiry, MessageData)],
    closedEnquiries: Int,
    openCases: Seq[(Case, OffsetDateTime)],
    closedCases: Int,
    declinedAppointments: Seq[AppointmentRender],
    provisionalAppointments: Seq[AppointmentRender],
    appointmentsNeedingOutcome: Seq[AppointmentRender],
    acceptedAppointments: Int,
    attendedAppointments: Int,
    cancelledAppointments: Int,
    clients: Map[UUID, Set[Either[UniversityID, SitsProfile]]],
  )
}

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  permissions: PermissionService,
  enquiries: EnquiryService,
  registrations: RegistrationService,
  audit: AuditService,
  cases: CaseService,
  profiles: ProfileService,
  clientSummaries: ClientSummaryService,
  appointments: AppointmentService,
  userLookupService: UserLookupService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import anyTeamActionRefiner._
  import securityService._

  private def clientHome(implicit request: AuthenticatedRequest[AnyContent]): Future[ServiceResult[ClientInformation]] = {
    val client = request.context.user.get.universityId.get

    ServiceResults.zip(
      enquiries.findEnquiriesForClient(client),
      cases.findForClient(client).map(_.map(_.filter(_.messages.nonEmpty))),
      registrations.get(client),
      appointments.findForClient(client)
    ).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      { case (clientEnquiries, clientCases, registration, a) =>
        val issues = (clientEnquiries.map(_.toIssue) ++ clientCases.map(_.toIssue)).sortBy(_.lastUpdatedDate)(JavaTime.dateTimeOrdering).reverse

        val usercodes = a.map(_.appointment.teamMember).toSet
        val userLookup = userLookupService.getUsers(usercodes.toSeq).toOption.getOrElse(Map())

        val result = Future.successful(Right(ClientInformation(issues, registration, a, userLookup)))

        // Record an EnquiryView or CaseView event for the first issue as that's open by default in the accordion
        issues.headOption.map(issue => issue.issue match {
          case e: Enquiry => audit.audit('EnquiryView, e.id.get.toString, 'Enquiry, Json.obj())(result)
          case c: Case => audit.audit('CaseView, c.id.get.toString, 'Case, Json.obj())(result)
          case _ => result
        }).getOrElse(result)
      }
    ))
  }

  private def teamMemberHome(implicit request: AuthenticatedRequest[AnyContent]): Future[ServiceResult[Option[TeamMemberInformation]]] = {
    val usercode = request.context.user.get.usercode

    permissions.teams(usercode).fold(
      errors => Future.successful(Left(errors)),
      teams => {
        if (teams.isEmpty) Future.successful(Right(None))
        else ServiceResults.zip(
          enquiries.findEnquiriesNeedingReply(usercode),
          enquiries.findEnquiriesAwaitingClient(usercode),
          enquiries.countClosedEnquiries(usercode),
          cases.listOpenCases(usercode),
          cases.countClosedCases(usercode),
          appointments.findDeclinedAppointments(usercode),
          appointments.findProvisionalAppointments(usercode),
          appointments.findAppointmentsNeedingOutcome(usercode),
          appointments.countAcceptedAppointments(usercode),
          appointments.countAttendedAppointments(usercode),
          appointments.countCancelledAppointments(usercode),
        ).successFlatMapTo { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases, declinedAppointments, provisionalAppointments, appointmentsNeedingOutcome, acceptedAppointments, attendedAppointments, cancelledAppointments) =>
          cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successFlatMapTo { caseClients =>
            val clients = (enquiriesNeedingReply ++ enquiriesAwaitingClient).map { case (e, _) => e.id.get -> Set(e.universityID) }.toMap ++ caseClients

            profiles.getProfiles(clients.values.flatten.toSet).successMapTo { clientProfiles =>
              val resolvedClients = clients.mapValues(_.map(c => clientProfiles.get(c).map(Right.apply).getOrElse(Left(c))))

              Some(TeamMemberInformation(
                teams,
                enquiriesNeedingReply,
                enquiriesAwaitingClient,
                closedEnquiries,
                openCases,
                closedCases,
                declinedAppointments,
                provisionalAppointments,
                appointmentsNeedingOutcome,
                acceptedAppointments,
                attendedAppointments,
                cancelledAppointments,
                resolvedClients,
              ))
            }
          }
        }
      }
    )
  }

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    ServiceResults.zip(
      clientHome,
      teamMemberHome
    ).successMap { case (clientInformation, teamMemberInformation) =>
      Ok(views.html.home(clientInformation, teamMemberInformation, uploadedFileControllerHelper.supportedMimeTypes))
    }
  }

  def closedEnquiries: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    enquiries.findClosedEnquiries(currentUser().usercode).successFlatMap { enquiries =>
      val clients = enquiries.map { case (e, _) => e.id.get -> Set(e.universityID) }.toMap

      profiles.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        Ok(views.html.admin.closedEnquiries(enquiries, resolvedClients))
      }
    }
  }

  def closedCases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    cases.listClosedCases(currentUser().usercode).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { clients =>
        profiles.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
          val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
          Ok(views.html.admin.closedCases(closedCases, resolvedClients))
        }
      }
    }
  }

  def atRiskClients: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    Future.successful(permissions.teams(currentUser().usercode)).successFlatMap(teams =>
      clientSummaries.findAtRisk(teams.contains(Teams.MentalHealth)).successMap(clients =>
        Ok(views.html.admin.atRiskClients(clients, teams.contains(Teams.MentalHealth)))
      )
    )
  }

  def acceptedAppointments: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments.findAcceptedAppointments(currentUser().usercode).successMap { appointments =>
      Ok(views.html.admin.acceptedAppointments(appointments, None))
    }
  }

  def attendedAppointments: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments.findAttendedAppointments(currentUser().usercode).successMap { appointments =>
      Ok(views.html.admin.attendedAppointments(appointments, None))
    }
  }

  def cancelledAppointments: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments.findCancelledAppointments(currentUser().usercode).successMap { appointments =>
      Ok(views.html.admin.cancelledAppointments(appointments, None))
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
