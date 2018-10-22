package controllers

import java.time.OffsetDateTime
import java.util.UUID

import controllers.IndexController.{ClientInformation, TeamMemberInformation}
import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.CaseDao.Case
import helpers.ServiceResults.ServiceResult
import helpers.{JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import services._
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

object IndexController {
  case class ClientInformation(
    issues: Seq[IssueRender],
    registration: Option[Registration],
    appointments: Seq[AppointmentRender]
  )

  case class TeamMemberInformation(
    teams: Seq[Team],
    enquiriesRequiringAction: Seq[(Enquiry, MessageData)],
    enquiriesAwaitingClient: Seq[(Enquiry, MessageData)],
    closedEnquiries: Int,
    openCases: Seq[(Case, OffsetDateTime)],
    closedCases: Int,
    caseClients: Map[UUID, Set[Client]],
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
  clientSummaries: ClientSummaryService,
  appointments: AppointmentService,
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

        val result = Future.successful(Right(ClientInformation(issues, registration, a)))

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
        ).successFlatMapTo { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases) =>
          cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successMapTo { caseClients =>
            Some(TeamMemberInformation(
              teams,
              enquiriesNeedingReply,
              enquiriesAwaitingClient,
              closedEnquiries,
              openCases,
              closedCases,
              caseClients
            ))
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
    enquiries.findClosedEnquiries(currentUser().usercode).successMap { enquiries =>
      Ok(views.html.admin.closedEnquiries(enquiries))
    }
  }

  def closedCases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    cases.listClosedCases(currentUser().usercode).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successMap { clients =>
        Ok(views.html.admin.closedCases(closedCases, clients))
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

  def appointments(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = start.map(_.toLocalDate),
      startBefore = end.map(_.toLocalDate),
      teamMember = Some(currentUser().usercode),
    )).successMap { appointments =>
      render {
        case Accepts.Json() =>
          Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(appointments)(Writes.seq(AppointmentRender.writer)))))
        case _ =>
          Redirect(routes.IndexController.home().withFragment("appointments"))
      }
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
