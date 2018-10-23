package controllers

import java.time.OffsetDateTime

import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.CaseDao.Case
import helpers.{JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import services._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  permissions: PermissionService,
  enquiryService: EnquiryService,
  registrations: RegistrationService,
  audit: AuditService,
  caseService: CaseService,
  clientSummaries: ClientSummaryService,
  appointments: AppointmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import anyTeamActionRefiner._
  import securityService._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    ServiceResults.zip(
      appointments.countForClientBadge(currentUser().universityId.get),
      Future.successful(permissions.teams(currentUser().usercode))
    ).successMap { case (count, teams) =>
      Ok(views.html.home(count, teams, uploadedFileControllerHelper.supportedMimeTypes))
    }
  }

  def enquiries: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      enquiryService.findEnquiriesNeedingReply(usercode),
      enquiryService.findEnquiriesAwaitingClient(usercode),
      enquiryService.countClosedEnquiries(usercode)
    ).successMap { case (requiringAction, awaitingClient, closedEnquiries) =>
      Ok(views.html.admin.enquiriesTab(
        requiringAction,
        awaitingClient,
        closedEnquiries,
        routes.IndexController.closedEnquiries(),
        "member",
        currentUser().usercode.string,
        " assigned to me"
      ))
    }
  }

  def closedEnquiries: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    enquiryService.findClosedEnquiries(currentUser().usercode).successMap { enquiries =>
      Ok(views.html.admin.closedEnquiries(enquiries))
    }
  }

  def cases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      caseService.listOpenCases(usercode),
      caseService.countClosedCases(usercode)
    ).successFlatMap { case (openCases, closedCases) =>
      caseService.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successMap { caseClients =>
        Ok(views.html.admin.casesTab(
          openCases,
          closedCases,
          caseClients,
          controllers.admin.routes.CaseController.createSelectTeam(),
          routes.IndexController.closedCases(),
          "member",
          currentUser().usercode.string,
          " assigned to me"
        ))
      }
    }
  }

  def closedCases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    caseService.listClosedCases(currentUser().usercode).successFlatMap { closedCases =>
      caseService.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successMap { clients =>
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

  def messages: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val client = currentUser().universityId.get
    ServiceResults.zip(
      enquiryService.findEnquiriesForClient(client),
      caseService.findForClient(client).map(_.map(_.filter(_.messages.nonEmpty))),
      registrations.get(client)
    ).successFlatMapTo { case (clientEnquiries, clientCases, registration) =>
      val issues = (clientEnquiries.map(_.toIssue) ++ clientCases.map(_.toIssue)).sortBy(_.lastUpdatedDate)(JavaTime.dateTimeOrdering).reverse

      val result = Future.successful(Right(Ok(views.html.messagesTab(
        issues,
        registration,
        uploadedFileControllerHelper.supportedMimeTypes
      ))))

      // Record an EnquiryView or CaseView event for the first issue
      issues.headOption.map(issue => issue.issue match {
        case e: Enquiry => audit.audit('EnquiryView, e.id.get.toString, 'Enquiry, Json.obj())(result)
        case c: Case => audit.audit('CaseView, c.id.get.toString, 'Case, Json.obj())(result)
        case _ => result
      }).getOrElse(result)
    }.successMap(r => r)
  }

  def clientAppointments: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    appointments.findForClient(currentUser().universityId.get).successMap(appointments =>
      Ok(views.html.clientAppointmentsTab(appointments))
    )
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
