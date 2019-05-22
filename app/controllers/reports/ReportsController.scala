package controllers.reports

import java.time.LocalDate

import controllers.refiners.ReportingAdminActionRefiner
import controllers.{API, BaseController, DateRange}
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent}
import services.{DailyMetrics, PermissionService, ReportingService}
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportsController @Inject()(
  reporting: ReportingService,
  permissions: PermissionService,
  reportingAdminActionRefiner: ReportingAdminActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import DailyMetrics.writesDailyMetrics
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
  
  def openedByDay(start: LocalDate, end: LocalDate): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    Future.successful(permissions.teams(currentUser().usercode))
      .successFlatMap(teams => {
        Future.sequence(teams.map(team => {
          reporting.openedEnquiriesByDay(start, end, Some(team))
            .flatMap(_.fold(
              e => Future.successful(Json.toJson(JsonClientError(status = "bad_request", errors = e.map(_.message)))),
              dms => Future.successful(Json.obj(team.name -> Json.toJson(dms)))
            ))
        })).map(metrics => Ok(Json.toJson(API.Success[JsArray](data = Json.arr(metrics)))))
      })
  }}
}
