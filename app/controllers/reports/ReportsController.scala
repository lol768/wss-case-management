package controllers.reports

import java.time.{DayOfWeek, LocalDate}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.github.tototoshi.csv.CSVWriter
import controllers.refiners.ReportingAdminActionRefiner
import controllers.{API, BaseController, DateRange, NamedDateRange}
import domain.Team
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Result}
import services.DailyMetrics.writesDailyMetrics
import services.{DailyMetrics, PermissionService, ReportingService}
import uk.ac.warwick.util.termdates
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

  type MetricsGenerator = (LocalDate, LocalDate, Team) => Future[ServiceResult[Seq[DailyMetrics]]]

  // this week, Monday-Sunday
  private def defaultDateRange = {
    val start = LocalDate.now.`with`(DayOfWeek.MONDAY)
    DateRange(
      start,
      start.plusDays(6)
    )
  }

  private def form = DateRange.form.fill(defaultDateRange)

  private def renderHtmlReport(dateRange: DateRange, dateForm: Form[DateRange] = form)(implicit request: AuthenticatedRequest[AnyContent]) = {
    val name = currentUser().name.first

    permissions.reportingTeams(currentUser().usercode)
      .map {
        case teams if teams.nonEmpty =>
          val start = dateRange.startTime
          val end = dateRange.endTime
          val range = dateRange.toString

          reporting.metrics(start, end, teams)
            .successMap { metrics => Ok(views.html.reports.reports(teams.map(_.name), range, metrics, teams.size > 1, dateForm)) }

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

  private def dailyReport(
    reporter: MetricsGenerator,
    dateRange: DateRange
  )(implicit req: AuthenticatedRequest[AnyContent], t: TimingContext): Future[Result] =
    Future.successful(permissions.reportingTeams(currentUser().usercode))
      .successFlatMap(teams => {
        Future.sequence(teams.map(team => {
          reporter(dateRange.start, dateRange.end, team)
            .flatMap(_.fold(
              e => Future.successful(Json.toJson(JsonClientError(status = "bad_request", errors = e.map(_.message)))),
              dms => Future.successful(Json.obj(team.name -> Json.toJson(dms)))
            ))
        })).map(metrics => Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(metrics)))))
      })

  def openedEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.openedEnquiriesByDay, DateRange(start, end))
  }}
  def closedEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.closedEnquiriesByDay, DateRange(start, end))
  }}
  def openedCasesFromEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.openedCasesFromEnquiriesByDay, DateRange(start, end))
  }}
  def closedCasesFromEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.closedCasesFromEnquiriesByDay, DateRange(start, end))
  }}
  def openedCasesWithoutEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.openedCasesWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def closedCasesWithoutEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.closedCasesWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def firstEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.firstEnquiriesByDay, DateRange(start, end))
  }}
  def casesWithAppointmentsFromEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.casesWithAppointmentsFromEnquiriesByDay, DateRange(start, end))
  }}
  def casesWithAppointmentsWithoutEnquiriesByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.casesWithAppointmentsWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def provisionalAppointmentsByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.provisionalAppointmentsByDay, DateRange(start, end))
  }}
  def acceptedAppointmentsByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.acceptedAppointmentsByDay, DateRange(start, end))
  }}
  def attendedAppointmentsByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.attendedAppointmentsByDay, DateRange(start, end))
  }}
  def cancelledAppointmentsByDay(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyReport(reporting.cancelledAppointmentsByDay, DateRange(start, end))
  }}

  private def csvSource(rows: Seq[Seq[String]]): Source[ByteString, Future[Unit]] = {
    StreamConverters.asOutputStream().mapMaterializedValue(outputStream => Future {
      val writer = CSVWriter.open(outputStream)
      try {
        writer.writeAll(rows)
      } catch {
        case t: Throwable => logger.error("Error encountered while writing CSV", t)
      } finally {
        writer.flush()
        writer.close()
      }
    })
  }

  private def dailyCsv(
    reporter: MetricsGenerator,
    dateRange: DateRange
  )(implicit req: AuthenticatedRequest[AnyContent], t: TimingContext): Future[Result] = {

    val emptyDailyMetrics = dateRange.map(d => DailyMetrics(d, 0))

    Future.successful(permissions.reportingTeams(currentUser().usercode))
      .successFlatMap(teams => {
        val futureMetrics = Future.sequence(teams.map(team => {
          reporter(dateRange.start, dateRange.end, team)
            .map(_.getOrElse(emptyDailyMetrics))
            .map(dms => (team, dms))
        }))

        futureMetrics.map(metrics => {
          val rows = ReportsController.csvFromMetrics(dateRange, metrics)
          Ok.chunked(csvSource(rows)).as("text/csv")
        })
      })
  }

  def openedEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request =>
    dailyCsv(reporting.openedEnquiriesByDay, DateRange(start, end))
  }
  def closedEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.closedEnquiriesByDay, DateRange(start, end))
  }}
  def openedCasesFromEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.openedCasesFromEnquiriesByDay, DateRange(start, end))
  }}
  def closedCasesFromEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.closedCasesFromEnquiriesByDay, DateRange(start, end))
  }}
  def openedCasesWithoutEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.openedCasesWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def closedCasesWithoutEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.closedCasesWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def firstEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.firstEnquiriesByDay, DateRange(start, end))
  }}
  def casesWithAppointmentsFromEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.casesWithAppointmentsFromEnquiriesByDay, DateRange(start, end))
  }}
  def casesWithAppointmentsWithoutEnquiriesByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.casesWithAppointmentsWithoutEnquiriesByDay, DateRange(start, end))
  }}
  def provisionalAppointmentsByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.provisionalAppointmentsByDay, DateRange(start, end))
  }}
  def acceptedAppointmentsByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.acceptedAppointmentsByDay, DateRange(start, end))
  }}
  def attendedAppointmentsByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.attendedAppointmentsByDay, DateRange(start, end))
  }}
  def cancelledAppointmentsByDayCsv(start: Option[LocalDate], end: Option[LocalDate]): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    dailyCsv(reporting.cancelledAppointmentsByDay, DateRange(start, end))
  }}
}

object ReportsController {

  def shortcuts: Seq[NamedDateRange] = {
    val now = LocalDate.now
    val startOfLastWeek = now.minusWeeks(1).`with`(DayOfWeek.MONDAY)
    val startOfAcademicYear = termdates.AcademicYear.forDate(now).getAcademicWeek(1).getDateRange.getStart

    Seq(
      NamedDateRange("Last week", "Last week, Monday-to-Sunday", DateRange(
        startOfLastWeek,
        startOfLastWeek.plusDays(6)
      )),
      NamedDateRange("Last month", "The last complete calendar month", DateRange(
        now.minusMonths(1).withDayOfMonth(1),
        now.withDayOfMonth(1).minusDays(1)
      )),
      NamedDateRange("Month to date", "The current calendar month (partial)", DateRange(
        now.withDayOfMonth(1),
        now.plusMonths(1).withDayOfMonth(1).minusDays(1)
      )),
      NamedDateRange("Academic year to date", "The current academic year (partial)", DateRange(
        startOfAcademicYear,
        startOfAcademicYear.plusYears(1)
      ))
    )
  }

  def csvFromMetrics(dateRange: DateRange, metrics: Seq[(Team, Seq[DailyMetrics])]): Seq[Seq[String]] = {
    val teamIds = metrics.map(_._1.name)
    val headerRow = "Date" +: teamIds

    val dataRowCount = dateRange.daysInclusive.intValue
    val dataMatrix = Array.ofDim[String](dataRowCount, headerRow.size)

    (0 until dataRowCount).map(d => {
      dataMatrix(d)(0) = dateRange.start.plusDays(d.longValue).toString
    })

    metrics.map {case (team, dms) => {
      val column = headerRow.indexOf(team.name)
      (0 until dataRowCount).map(d => {
        dataMatrix(d)(column) = dms(d).value.toString
      })
    }}

    val dataRows = (0 until dataRowCount).map(d => {
      dataMatrix(d).toSeq
    })

    headerRow +: dataRows
  }
}
