package controllers.reports

import java.time.LocalDate

import controllers.refiners.ReportingAdminActionRefiner
import controllers.{API, BaseController, DateRange}
import domain.Team
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Result}
import services.DailyMetrics.writesDailyMetrics
import services.{DailyMetrics, PermissionService, ReportingService}
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportsController @Inject()(
  reporting: ReportingService,
  permissions: PermissionService,
  reportingAdminActionRefiner: ReportingAdminActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import reportingAdminActionRefiner._

  private def defaultDateRange = DateRange(
    LocalDate.now().minusDays(7),
    LocalDate.now()
  )

  private def form = DateRange.form.fill(defaultDateRange)

  private def renderHtmlReport(dateRange: DateRange, dateForm: Form[DateRange] = form)(implicit request: AuthenticatedRequest[AnyContent]) = {
    val name = currentUser().name.first

    permissions.teams(currentUser().usercode)
      .map {
        case teams if teams.nonEmpty =>
          val start = dateRange.startTime
          val end = dateRange.endTime
          val range = dateRange.toString

          reporting.metrics(start, end, teams)
            .successMap { metrics => Ok(views.html.reports.home(teams.map(_.name), range, metrics, teams.size > 1, dateForm)) }

        case _ =>
          Future.successful(Forbidden(views.html.errors.forbidden(name)(requestContext(request))))
      }.getOrElse(Future.successful(Forbidden(views.html.errors.forbidden(name)(requestContext(request)))))
  }

  def reportForm: Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request =>
    renderHtmlReport(defaultDateRange, form)
  }

  def report: Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request =>
    form.bindFromRequest.fold(
      formError => renderHtmlReport(defaultDateRange, formError),
      dateRange => renderHtmlReport(dateRange, form.fill(dateRange))
    )
  }
  
  private def dailyReportOptTeam(
    reporter: (LocalDate, LocalDate, Option[Team]) => Future[ServiceResult[Seq[DailyMetrics]]],
    start: LocalDate,
    end: LocalDate
  )(implicit req: AuthenticatedRequest[AnyContent], t: TimingContext): Future[Result] = 
    Future.successful(permissions.teams(currentUser().usercode))
      .successFlatMap(teams => {
        Future.sequence(teams.map(team => {
          reporter(start, end, Some(team))
            .flatMap(_.fold(
              e => Future.successful(Json.toJson(JsonClientError(status = "bad_request", errors = e.map(_.message)))),
              dms => Future.successful(Json.obj(team.name -> Json.toJson(dms)))
            ))
        })).map(metrics => Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(metrics)))))
      })

  private def dailyReport(
    reporter: (LocalDate, LocalDate, Team) => Future[ServiceResult[Seq[DailyMetrics]]],
    start: LocalDate,
    end: LocalDate
  )(implicit req: AuthenticatedRequest[AnyContent], t: TimingContext): Future[Result] =
    Future.successful(permissions.teams(currentUser().usercode))
      .successFlatMap(teams => {
        Future.sequence(teams.map(team => {
          reporter(start, end, team)
            .flatMap(_.fold(
              e => Future.successful(Json.toJson(JsonClientError(status = "bad_request", errors = e.map(_.message)))),
              dms => Future.successful(Json.obj(team.name -> Json.toJson(dms)))
            ))
        })).map(metrics => Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(metrics)))))
      })
  
  def openedEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.openedEnquiriesByDay, start, end)
  }}
  def closedEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.closedEnquiriesByDay, start, end)
  }}
  def openedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.openedCasesFromEnquiriesByDay, start, end)
  }}
  def closedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.closedCasesFromEnquiriesByDay, start, end)
  }}
  def openedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.openedCasesWithoutEnquiriesByDay, start, end)
  }}
  def closedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.closedCasesWithoutEnquiriesByDay, start, end)
  }}
  def firstEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.firstEnquiriesByDay, start, end)
  }}
  def casesWithAppointmentsFromEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.casesWithAppointmentsFromEnquiriesByDay, start, end)
  }}
  def casesWithAppointmentsWithoutEnquiriesByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.casesWithAppointmentsWithoutEnquiriesByDay, start, end)
  }}
  def provisionalAppointmentsByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.provisionalAppointmentsByDay, start, end)
  }}
  def acceptedAppointmentsByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.acceptedAppointmentsByDay, start, end)
  }}
  def attendedAppointmentsByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.attendedAppointmentsByDay, start, end)
  }}
  def cancelledAppointmentsByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReportOptTeam(reporting.cancelledAppointmentsByDay, start, end)
  }}



}
