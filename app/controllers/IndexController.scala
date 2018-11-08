package controllers

import java.time.OffsetDateTime

import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import services._
import warwick.core.helpers.JavaTime

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
  userPreferences: UserPreferencesService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import anyTeamActionRefiner._
  import securityService._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    ServiceResults.zip(
      appointments.countForClientBadge(currentUser().universityId.get),
      Future.successful(permissions.teams(currentUser().usercode)),
      userPreferences.get(currentUser().usercode),
    ).successMap { case (count, teams, prefs) =>
      Ok(views.html.home(count, teams, uploadedFileControllerHelper.supportedMimeTypes, prefs))
    }
  }

  def enquiries: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(usercode),
      enquiryService.findEnquiriesNeedingReply(usercode, Pagination.firstPage()),
      enquiryService.countEnquiriesAwaitingClient(usercode),
      enquiryService.findEnquiriesAwaitingClient(usercode, Pagination.firstPage()),
      enquiryService.countClosedEnquiries(usercode)
    ).successMap { case (requiringActionCount, requiringAction, awaitingClientCount, awaitingClient, closedEnquiries) =>
      Ok(views.html.admin.enquiriesTab(
        requiringAction,
        Pagination(requiringActionCount, 0, controllers.routes.IndexController.enquiriesNeedingReply()),
        awaitingClient,
        Pagination(awaitingClientCount, 0, controllers.routes.IndexController.enquiriesAwaitingClient()),
        closedEnquiries,
        routes.IndexController.closedEnquiries(),
        "member",
        currentUser().usercode.string,
        " assigned to me"
      ))
    }
  }

  def enquiriesNeedingReply(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(usercode),
      enquiryService.findEnquiriesNeedingReply(usercode, Pagination.asPage(page))
    ).successMap { case (requiringActionCount, requiringAction) =>
      val pagination = Pagination(requiringActionCount, page, controllers.routes.IndexController.enquiriesNeedingReply())
      Ok(views.html.admin.enquiriesNeedingReply(requiringAction, pagination))
    }
  }

  def enquiriesAwaitingClient(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      enquiryService.countEnquiriesAwaitingClient(usercode),
      enquiryService.findEnquiriesAwaitingClient(usercode, Pagination.asPage(page))
    ).successMap { case (awaitingClientCount, awaitingClient) =>
      val pagination = Pagination(awaitingClientCount, page, controllers.routes.IndexController.enquiriesAwaitingClient())
      Ok(views.html.admin.enquiriesAwaitingClient(awaitingClient, pagination))
    }
  }

  def closedEnquiries(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      enquiryService.countClosedEnquiries(usercode),
      enquiryService.findClosedEnquiries(usercode, Pagination.asPage(page))
    ).successMap { case (closed, closedEnquiries) =>
      val pagination = Pagination(closed, page, controllers.routes.IndexController.closedEnquiries())
      Ok(views.html.admin.closedEnquiries(closedEnquiries, pagination))
    }
  }

  def cases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      caseService.countOpenCases(usercode),
      caseService.listOpenCases(usercode, Pagination.firstPage()),
      caseService.countClosedCases(usercode)
    ).successFlatMap { case (open, openCases, closedCases) =>
      val pagination = Pagination(open, 0, controllers.routes.IndexController.openCases())
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { caseClients =>
        Ok(views.html.admin.casesTab(
          openCases,
          pagination,
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

  def openCases(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      caseService.countOpenCases(usercode),
      caseService.listOpenCases(usercode, Pagination.asPage(page))
    ).successFlatMap { case (open, openCases) =>
      val pagination = Pagination(open, page, controllers.routes.IndexController.openCases())
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.openCases(openCases, clients, pagination))
      }
    }
  }

  def closedCases(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = currentUser().usercode
    ServiceResults.zip(
      caseService.countClosedCases(usercode),
      caseService.listClosedCases(usercode, Pagination.asPage(page))
    ).successFlatMap { case (closed, closedCases) =>
      val pagination = Pagination(closed, page, controllers.routes.IndexController.closedCases())
      caseService.getClients(closedCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.closedCases(closedCases, clients, pagination))
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
      enquiryService.findAllEnquiriesForClient(client),
      caseService.findAllForClient(client).map(_.map(_.filter(_.messages.nonEmpty))),
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
        case e: Enquiry => audit.audit('EnquiryView, e.id.toString, 'Enquiry, Json.obj())(result)
        case c: Case => audit.audit('CaseView, c.id.toString, 'Case, Json.obj())(result)
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
