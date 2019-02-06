package controllers.admin

import java.time.OffsetDateTime

import controllers.refiners.{AnyTeamActionRefiner, CanViewTeamActionRefiner}
import controllers.{API, BaseController}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.CaseDao.CaseFilter
import domain.dao.EnquiryDao.EnquiryFilter
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc._
import services._
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object AdminController {
  val ownersFilterForm: Form[Set[Usercode]] = Form(
    single(
      "usercodes" -> set(nonEmptyText.transform(s => Usercode(s), (u: Usercode) => u.string))
    )
  )
}

@Singleton
class AdminController @Inject()(
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  enquiryService: EnquiryService,
  caseService: CaseService,
  ownerService: OwnerService,
  clientSummaryService: ClientSummaryService,
  userLookupService: UserLookupService,
  appointments: AppointmentService,
  userPreferences: UserPreferencesService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import canViewTeamActionRefiner._

  def teamHome(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    userPreferences.get(currentUser().usercode).successMap { prefs =>
      Ok(views.html.admin.teamHome(teamRequest.team, prefs))
    }
  }

  def enquiries(teamId: String): Action[AnyContent] = CanViewTeamAjaxAction(teamId).async { implicit teamRequest =>
    val filter = EnquiryFilter(teamRequest.team)

    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(filter),
      enquiryService.findEnquiriesNeedingReply(filter, Pagination.firstPage()),
      enquiryService.countEnquiriesAwaitingClient(filter),
      enquiryService.findEnquiriesAwaitingClient(filter, Pagination.firstPage()),
      enquiryService.getOwnersMatching(filter, IssueStateFilter.Open),
      enquiryService.countClosedEnquiries(filter),
    ).successFlatMap { case (requiringActionCount, requiringAction, awaitingClientCount, awaitingClient, allOpenOwners, closedEnquiries) =>
      ServiceResults.zip(
        enquiryService.getOwners(requiringAction.map(_.enquiry.id).toSet),
        enquiryService.getOwners(awaitingClient.map(_.enquiry.id).toSet),
      ).successMap { case (requiringActionOwners, awaitingClientOwners) =>
        Ok(views.html.admin.enquiriesTab(
          requiringAction,
          Some(requiringActionOwners),
          Pagination(requiringActionCount, 0, controllers.admin.routes.AdminController.enquiriesNeedingReply(teamRequest.team.id)),
          awaitingClient,
          Some(awaitingClientOwners),
          Pagination(awaitingClientCount, 0, controllers.admin.routes.AdminController.enquiriesAwaitingClient(teamRequest.team.id)),
          closedEnquiries,
          controllers.admin.routes.AdminController.closedEnquiries(teamId),
          Some(allOpenOwners),
          "team",
          teamId,
          s" assigned to ${teamRequest.team.name}"
        ))
      }
    }
  }

  def enquiriesNeedingReply(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val form = AdminController.ownersFilterForm.bindFromRequest()
    val baseFilter = EnquiryFilter(teamRequest.team)
    val filter = baseFilter.withOwners(form.value.getOrElse(Set.empty))

    ServiceResults.zip(
      enquiryService.countEnquiriesNeedingReply(filter),
      enquiryService.findEnquiriesNeedingReply(filter, Pagination.asPage(page)),
      enquiryService.getOwnersMatching(filter, IssueStateFilter.Open),
    ).successFlatMap { case (requiringActionCount, requiringAction, allOwners) =>
      val pagination = Pagination(requiringActionCount, page, controllers.admin.routes.AdminController.enquiriesNeedingReply(teamRequest.team.id))
      enquiryService.getOwners(requiringAction.map(_.enquiry.id).toSet).successMap { owners =>
        Ok(views.html.admin.enquiriesNeedingReply(requiringAction, Some(owners), Some(allOwners), Some(form), pagination))
      }
    }
  }

  def enquiriesAwaitingClient(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val form = AdminController.ownersFilterForm.bindFromRequest()
    val baseFilter = EnquiryFilter(teamRequest.team)
    val filter = baseFilter.withOwners(form.value.getOrElse(Set.empty))

    ServiceResults.zip(
      enquiryService.countEnquiriesAwaitingClient(filter),
      enquiryService.findEnquiriesAwaitingClient(filter, Pagination.asPage(page)),
      enquiryService.getOwnersMatching(filter, IssueStateFilter.Open),
    ).successFlatMap { case (awaitingClientCount, awaitingClient, allOwners) =>
      val pagination = Pagination(awaitingClientCount, page, controllers.admin.routes.AdminController.enquiriesAwaitingClient(teamRequest.team.id))
      enquiryService.getOwners(awaitingClient.map(_.enquiry.id).toSet).successMap { owners =>
        Ok(views.html.admin.enquiriesAwaitingClient(awaitingClient, Some(owners), Some(allOwners), Some(form), pagination))
      }
    }
  }

  def closedEnquiries(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val form = AdminController.ownersFilterForm.bindFromRequest()
    val baseFilter = EnquiryFilter(teamRequest.team)
    val filter = baseFilter.withOwners(form.value.getOrElse(Set.empty))

    ServiceResults.zip(
      enquiryService.countClosedEnquiries(filter),
      enquiryService.findClosedEnquiries(filter, Pagination.asPage(page)),
      enquiryService.getOwnersMatching(filter, IssueStateFilter.Open),
    ).successFlatMap { case (closed, closedEnquiries, allOwners) =>
      val pagination = Pagination(closed, page, controllers.admin.routes.AdminController.closedEnquiries(teamRequest.team.id))
      enquiryService.getOwners(closedEnquiries.map(_.enquiry.id).toSet).successMap { owners =>
        Ok(views.html.admin.closedEnquiries(closedEnquiries, Some(owners), Some(allOwners), Some(form), pagination))
      }
    }
  }

  def cases(teamId: String): Action[AnyContent] = CanViewTeamAjaxAction(teamId).async { implicit teamRequest =>
    val caseFilter = CaseFilter(teamRequest.team)
    val openCaseFilter = caseFilter.withState(IssueStateFilter.Open)
    val closedCaseFilter = caseFilter.withState(IssueStateFilter.Closed)

    ServiceResults.zip(
      caseService.countCases(openCaseFilter),
      caseService.listCases(openCaseFilter, Pagination.firstPage()),
      caseService.countCases(closedCaseFilter),
      caseService.getOwnersMatching(openCaseFilter),
    ).successFlatMap { case (open, openCases, closedCases, allOpenCaseOwners) =>
      val pagination = Pagination(open, 0, controllers.admin.routes.AdminController.openCases(teamRequest.team.id))
      ServiceResults.zip(
        caseService.getClients(openCases.map(_.clientCase.id).toSet),
        caseService.getOwners(openCases.map(_.clientCase.id).toSet),
      ).successMap { case (caseClients, caseOwners) =>
        Ok(views.html.admin.casesTab(
          openCases,
          pagination,
          closedCases,
          caseClients,
          Some(caseOwners),
          Some(allOpenCaseOwners),
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
    val form = AdminController.ownersFilterForm.bindFromRequest()
    val baseFilter = CaseFilter(teamRequest.team).withState(IssueStateFilter.Open)
    val openCaseFilter = baseFilter.withOwners(form.value.getOrElse(Set.empty))

    ServiceResults.zip(
      caseService.countCases(openCaseFilter),
      caseService.listCases(openCaseFilter, Pagination.asPage(page)),
      caseService.getOwnersMatching(baseFilter),
    ).successFlatMap { case (open, openCases, allOwners) =>
      val pagination = Pagination(open, page, controllers.admin.routes.AdminController.openCases(teamRequest.team.id))
      ServiceResults.zip(
        caseService.getClients(openCases.map(_.clientCase.id).toSet),
        caseService.getOwners(openCases.map(_.clientCase.id).toSet),
      ).successMap { case (clients, owners) =>
        Ok(views.html.admin.openCases(openCases, clients, Some(owners), Some(allOwners), Some(form), pagination))
      }
    }
  }

  def closedCases(teamId: String, page: Int): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val form = AdminController.ownersFilterForm.bindFromRequest()
    val baseFilter = CaseFilter(teamRequest.team).withState(IssueStateFilter.Closed)
    val closedCaseFilter = baseFilter.withOwners(form.value.getOrElse(Set.empty))

    ServiceResults.zip(
      caseService.countCases(closedCaseFilter),
      caseService.listCases(closedCaseFilter, Pagination.asPage(page)),
      caseService.getOwnersMatching(baseFilter),
    ).successFlatMap { case (closed, closedCases, allOwners) =>
      val pagination = Pagination(closed, page, controllers.admin.routes.AdminController.closedCases(teamRequest.team.id))
      ServiceResults.zip(
        caseService.getClients(closedCases.map(_.clientCase.id).toSet),
        caseService.getOwners(closedCases.map(_.clientCase.id).toSet),
      ).successMap { case (clients, owners) =>
        Ok(views.html.admin.closedCases(closedCases, clients, Some(owners), Some(allOwners), Some(form), pagination))
      }
    }
  }

  def atRiskClients(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    clientSummaryService.findAtRisk(teamRequest.team == Teams.MentalHealth).successMap(clients =>
      Ok(views.html.admin.atRiskClients(clients))
    )
  }

  private def appointments(start: Option[OffsetDateTime], end: Option[OffsetDateTime], team: Option[Team], redirect: Call)(implicit request: RequestHeader): Future[Result] =
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = start.map(_.toLocalDate),
      startBefore = end.map(_.toLocalDate),
      team = team,
    )).successMap { appointments =>
      render {
        case Accepts.Json() =>
          Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(appointments)(Writes.seq(AppointmentRender.writer)))))
        case _ =>
          Redirect(redirect)
      }
    }

  def appointmentsAllTeams(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    appointments(start, end, None, controllers.routes.IndexController.home().withFragment("appointments"))
  }

  def appointments(teamId: String, start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    appointments(start, end, Some(teamRequest.team), controllers.admin.routes.AdminController.teamHome(teamId).withFragment("appointments"))
  }

}
