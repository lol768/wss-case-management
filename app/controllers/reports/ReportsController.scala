package controllers.reports

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import controllers.BaseController
import controllers.refiners.ReportingAdminActionRefiner
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{PermissionService, ReportingService}
import warwick.core.helpers.JavaTime

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportsController @Inject()(
  reporting: ReportingService,
  permissions: PermissionService,
  reportingAdminActionRefiner: ReportingAdminActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import reportingAdminActionRefiner._
  
  def home(): Action[AnyContent] = ReportingAdminRequiredAction.async { implicit request => {
    val name = currentUser().name.first

    permissions.teams(currentUser().usercode)
      .map {
        case teams if teams.nonEmpty =>
          val start = JavaTime.offsetDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(14.toLong)
          val end = OffsetDateTime.now
          val range = "Last 14 days"

          reporting.metrics(start, end, teams)
            .successMap { metrics => Ok(views.html.reports.home(teams.map(_.name), range, metrics, teams.size > 1)) }

        case _ =>
          Future.successful(Forbidden(views.html.errors.forbidden(name)(requestContext(request))))
      }.getOrElse(Future.successful(Forbidden(views.html.errors.forbidden(name)(requestContext(request)))))
  }}
}
