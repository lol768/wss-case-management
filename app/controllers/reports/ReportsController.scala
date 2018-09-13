package controllers.reports

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import controllers.BaseController
import controllers.refiners.AdminActionRefiner
import domain.{Team, Teams}
import helpers.{JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, EnquiryService}

import scala.concurrent.{ExecutionContext, Future}
import ReportsController._
import helpers.ServiceResults.ServiceResult

object ReportsController {
  val ReportDays = Seq(7, 14, 28)
  case class Statistics(last7Days: Int, last14Days: Int, last28Days: Int)
}

@Singleton
class ReportsController @Inject()(
  enquiries: EnquiryService,
  cases: CaseService,
  adminActionRefiner: AdminActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import adminActionRefiner._

  private def collectStatistics(report: (Team, OffsetDateTime) => Future[ServiceResult[Int]]): Future[ServiceResult[Seq[(Team, Statistics)]]] =
    ServiceResults.futureSequence(
      Teams.all.map { team =>
        ServiceResults.futureSequence(
          ReportDays.map { d =>
            report(team, JavaTime.offsetDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(d.toLong))
          }
        ).map(_.right.map(_.toList match {
          case List(last7, last14, last28) => team -> Statistics(last7, last14, last28)
          case r => throw new IllegalArgumentException(s"Invalid results: $r")
        }))
      }
    )

  def home(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    val openedEnquiries: Future[ServiceResult[Seq[(Team, Statistics)]]] =
      collectStatistics(enquiries.countEnquiriesOpenedSince)
    val closedEnquiries: Future[ServiceResult[Seq[(Team, Statistics)]]] =
      collectStatistics(enquiries.countEnquiriesClosedSince)
    val openedCases: Future[ServiceResult[Seq[(Team, Statistics)]]] =
      collectStatistics(cases.countOpenedSince)
    val closedCases: Future[ServiceResult[Seq[(Team, Statistics)]]] =
      collectStatistics(cases.countClosedSince)

    ServiceResults.zip(
      openedEnquiries,
      closedEnquiries,
      openedCases,
      closedCases
    ).successMap { case (oe, ce, oc, cc) =>
      Ok(views.html.reports.home(oe, ce, oc, cc))
    }
  }

}
