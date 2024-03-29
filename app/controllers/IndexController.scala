package controllers

import java.time.OffsetDateTime

import controllers.MessagesController.MessageFormData
import controllers.refiners.AnyTeamActionRefiner
import domain.AuditEvent._
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.CaseDao.CaseFilter
import domain.dao.EnquiryDao.EnquiryFilter
import warwick.core.helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import services._
import services.tabula.ProfileService
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.sso.AuthenticatedRequest

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
  clients: ClientService,
  clientSummaries: ClientSummaryService,
  appointments: AppointmentService,
  userPreferences: UserPreferencesService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  profiles: ProfileService,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import anyTeamActionRefiner._
  import securityService._

  private[this] val clientUserTypes = configuration.get[Seq[String]]("wellbeing.validClientUserTypes").flatMap(UserType.namesToValuesMap.get)

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    ServiceResults.zip(
      appointments.countForClientBadge(currentUser().universityId.get),
      Future.successful(permissions.teams(currentUser().usercode)),
      userPreferences.get(currentUser().usercode),
    ).successMap { case (count, teams, prefs) =>
      Ok(views.html.home(count, teams, uploadedFileControllerHelper.supportedMimeTypes, prefs))
    }
  }

  def enquiries: Action[AnyContent] = AnyTeamMemberRequiredAjaxAction.async { implicit request =>
    val filter = EnquiryFilter(currentUser().usercode)

    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(filter),
      enquiryService.findEnquiriesNeedingReply(filter, IssueListFilter.empty, Pagination.firstPage()),
      enquiryService.countEnquiriesAwaitingClient(filter),
      enquiryService.findEnquiriesAwaitingClient(filter, IssueListFilter.empty, Pagination.firstPage()),
      enquiryService.countClosedEnquiries(filter)
    ).successMap { case (requiringActionCount, requiringAction, awaitingClientCount, awaitingClient, closedEnquiries) =>
      Ok(views.html.admin.enquiriesTab(
        requiringAction,
        None,
        Pagination(requiringActionCount, 0, controllers.routes.IndexController.enquiriesNeedingReply()),
        awaitingClient,
        None,
        Pagination(awaitingClientCount, 0, controllers.routes.IndexController.enquiriesAwaitingClient()),
        closedEnquiries,
        controllers.admin.routes.TeamEnquiryController.createSelectTeam(),
        routes.IndexController.closedEnquiries(),
        None,
        "member",
        currentUser().usercode.string,
        " assigned to me"
      ))
    }
  }

  def enquiriesNeedingReply(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val filter = EnquiryFilter(currentUser().usercode)

    val issueListFilterForm = IssueListFilter.form.bindFromRequest()
    val issueListFilter = issueListFilterForm.value.getOrElse(IssueListFilter.empty)

    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(filter),
      enquiryService.findEnquiriesNeedingReply(filter, issueListFilter, Pagination.asPage(page))
    ).successMap { case (requiringActionCount, requiringAction) =>
      val pagination = Pagination(requiringActionCount, page, controllers.routes.IndexController.enquiriesNeedingReply())
      Ok(views.html.admin.enquiriesNeedingReply(requiringAction, None, None, None, Some(issueListFilterForm), pagination))
    }
  }

  def enquiriesAwaitingClient(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val filter = EnquiryFilter(currentUser().usercode)

    val issueListFilterForm = IssueListFilter.form.bindFromRequest()
    val issueListFilter = issueListFilterForm.value.getOrElse(IssueListFilter.empty)

    ServiceResults.zip(
      enquiryService.countEnquiriesAwaitingClient(filter),
      enquiryService.findEnquiriesAwaitingClient(filter, issueListFilter, Pagination.asPage(page))
    ).successMap { case (awaitingClientCount, awaitingClient) =>
      val pagination = Pagination(awaitingClientCount, page, controllers.routes.IndexController.enquiriesAwaitingClient())
      Ok(views.html.admin.enquiriesAwaitingClient(awaitingClient, None, None, None, Some(issueListFilterForm), pagination))
    }
  }

  def closedEnquiries(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val filter = EnquiryFilter(currentUser().usercode)

    val issueListFilterForm = IssueListFilter.form.bindFromRequest()
    val issueListFilter = issueListFilterForm.value.getOrElse(IssueListFilter.empty)

    ServiceResults.zip(
      enquiryService.countClosedEnquiries(filter),
      enquiryService.findClosedEnquiries(filter, issueListFilter, Pagination.asPage(page))
    ).successMap { case (closed, closedEnquiries) =>
      val pagination = Pagination(closed, page, controllers.routes.IndexController.closedEnquiries())
      Ok(views.html.admin.closedEnquiries(closedEnquiries, None, None, None, Some(issueListFilterForm), pagination))
    }
  }

  def cases: Action[AnyContent] = AnyTeamMemberRequiredAjaxAction.async { implicit request =>
    val caseFilter = CaseFilter(currentUser().usercode)
    val openCaseFilter = caseFilter.withState(IssueStateFilter.Open)
    val closedCaseFilter = caseFilter.withState(IssueStateFilter.Closed)

    ServiceResults.zip(
      caseService.countCases(openCaseFilter),
      caseService.listCases(openCaseFilter, IssueListFilter.empty, Pagination.firstPage()),
      caseService.countCases(closedCaseFilter)
    ).successFlatMap { case (open, openCases, closedCases) =>
      val pagination = Pagination(open, 0, controllers.routes.IndexController.openCases())
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { caseClients =>
        Ok(views.html.admin.casesTab(
          openCases,
          pagination,
          closedCases,
          caseClients,
          None,
          None,
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
    val openCaseFilter = CaseFilter(currentUser().usercode).withState(IssueStateFilter.Open)

    val issueListFilterForm = IssueListFilter.form.bindFromRequest()
    val issueListFilter = issueListFilterForm.value.getOrElse(IssueListFilter.empty)

    ServiceResults.zip(
      caseService.countCases(openCaseFilter),
      caseService.listCases(openCaseFilter, issueListFilter, Pagination.asPage(page))
    ).successFlatMap { case (open, openCases) =>
      val pagination = Pagination(open, page, controllers.routes.IndexController.openCases())
      caseService.getClients(openCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.openCases(openCases, clients, None, None, None, Some(issueListFilterForm), pagination))
      }
    }
  }

  def closedCases(page: Int): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val closedCaseFilter = CaseFilter(currentUser().usercode).withState(IssueStateFilter.Closed)

    val issueListFilterForm = IssueListFilter.form.bindFromRequest()
    val issueListFilter = issueListFilterForm.value.getOrElse(IssueListFilter.empty)

    ServiceResults.zip(
      caseService.countCases(closedCaseFilter),
      caseService.listCases(closedCaseFilter, issueListFilter, Pagination.asPage(page))
    ).successFlatMap { case (closed, closedCases) =>
      val pagination = Pagination(closed, page, controllers.routes.IndexController.closedCases())
      caseService.getClients(closedCases.map(_.clientCase.id).toSet).successMap { clients =>
        Ok(views.html.admin.closedCases(closedCases, clients, None, None, None, Some(issueListFilterForm), pagination))
      }
    }
  }

  def atRiskClients: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    Future.successful(permissions.teams(currentUser().usercode)).successFlatMap(teams =>
      clientSummaries.findAtRisk(teams.contains(Teams.MentalHealth)).successMap(clients =>
        Ok(views.html.admin.atRiskClients(clients))
      )
    )
  }

  def appointmentsTab: Action[AnyContent] = AnyTeamMemberRequiredAjaxAction.async { implicit request =>
    ServiceResults.zip(
      appointments.countForSearch(appointmentsNeedingOutcomesSearch),
      userPreferences.get(currentUser().usercode),
    ).successMap { case (appointmentsNeedingOutcomes, preferences) =>
      Ok(views.html.admin.appointmentsTab(
        routes.IndexController.appointments(),
        None,
        appointmentsNeedingOutcomes,
        Some(routes.IndexController.appointmentsNeedingOutcomes()),
        controllers.admin.routes.AppointmentController.createSelectTeam(),
        "member",
        currentUser().usercode.string,
        " assigned to me",
        preferences,
      ))
    }
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

  private def appointmentsNeedingOutcomesSearch(implicit request: AuthenticatedRequest[_]) =
    AppointmentSearchQuery(
      teamMember = Some(currentUser().usercode),
      states = Set(AppointmentState.Provisional, AppointmentState.Accepted, AppointmentState.Attended),
      hasOutcome = Some(false),
      endBefore = Some(JavaTime.offsetDateTime),
    )

  def appointmentsNeedingOutcomes: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments.findForSearch(appointmentsNeedingOutcomesSearch)
      .successMap { appointments =>
        Ok(views.html.admin.appointmentTable(None, appointments, Some("There are no appointments needing their outcomes recording")))
      }
  }

  def messages: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val universityID = currentUser().universityId.get
    ServiceResults.zip(
      clients.find(universityID),
      enquiryService.findAllEnquiriesForClient(universityID),
      caseService.findAllForClient(universityID).map(_.map(_.filter(_.messages.nonEmpty))),
      registrations.get(universityID),
      profiles.getProfile(universityID).map(_.value),
    ).successFlatMapTo { case (client, clientEnquiries, clientCases, registration, profile) =>
      ServiceResults.zip(
        enquiryService.getLastUpdatedMessageDates(clientEnquiries.map(_.enquiry.id).toSet),
        caseService.getLastUpdatedMessageDates(clientCases.map(_.clientCase.id).toSet),
      ).successFlatMapTo { case (enquiryLastMessageMap, caseLastMessageMap) =>
        val enquiryIssues = clientEnquiries.map(_.toIssue)
        val caseIssues = clientCases.map(_.toIssue)

        val messageForms: Map[IssueRender, Form[MessageFormData]] =
          enquiryIssues.map(issue => issue -> MessagesController.messageForm(enquiryLastMessageMap.get(issue.issue.id)).fill(MessageFormData("", Set.empty, enquiryLastMessageMap.get(issue.issue.id)))).toMap ++
          caseIssues.map { issue =>
            val lastMessageDate = caseLastMessageMap.getOrElse(issue.issue.id, Map()).get(universityID)
            issue -> MessagesController.messageForm(lastMessageDate).fill(MessageFormData("", Set.empty, lastMessageDate))
          }

        val allIssues = (enquiryIssues ++ caseIssues).sortBy(_.lastUpdatedDate)(JavaTime.dateTimeOrdering).reverse

        val result = Future.successful(Right(Ok(views.html.messagesTab(
          client.getOrElse(Client.transient(currentUser())),
          allIssues,
          registration,
          uploadedFileControllerHelper.supportedMimeTypes,
          canMakeEnquiry = clientUserTypes.contains(profile.map(_.userType).getOrElse(UserType(currentUser()))),
          messageForms
        ))))

        // Record an EnquiryView or CaseView event for the first issue
        allIssues.headOption.map(issue => issue.issue match {
          case e: Enquiry => audit.audit(Operation.Enquiry.View, e.id.toString, Target.Enquiry, Json.obj())(result)
          case c: Case => audit.audit(Operation.Case.View, c.id.toString, Target.Case, Json.obj())(result)
          case _ => result
        }).getOrElse(result)
      }
    }.successMap(r => r)
  }

  def clientAppointments: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    appointments.findForClient(currentUser().universityId.get).successMap(appointments =>
      Ok(views.html.clientAppointmentsTab(appointments))
    )
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY): Action[AnyContent] = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
