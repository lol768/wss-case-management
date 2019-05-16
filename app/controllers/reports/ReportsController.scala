package controllers.reports

import java.time.LocalDate

import controllers.refiners.ReportingAdminActionRefiner
import controllers.{BaseController, DateRange}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent}
import services.{PermissionService, ReportingService}
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

  def showForm: Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request =>
    renderHtmlReport(defaultDateRange, form)
  }

  def submitForm: Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request =>
    form.bindFromRequest.fold(
      formError => renderHtmlReport(defaultDateRange, formError),
      dateRange => renderHtmlReport(dateRange, form.fill(dateRange))
    )
  }
}
