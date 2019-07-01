package controllers.reports

import java.time.LocalDate

import controllers.DateRange
import domain.Teams
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.DailyMetrics

class ReportsControllerTest extends PlaySpec with MockitoSugar {
  "reports controller" should {
    "build CSV rows from metrics" in {
      val start = LocalDate.of(2018, 10, 1)
      val end = start.plusDays(6L)
      val dateRange = DateRange(start, end)

      val metrics = Seq(
        (Teams.Counselling, Seq(
          DailyMetrics(start, 0),
          DailyMetrics(start.plusDays(1), 0),
          DailyMetrics(start.plusDays(2), 1),
          DailyMetrics(start.plusDays(3), 2),
          DailyMetrics(start.plusDays(4), 1),
          DailyMetrics(start.plusDays(5), 1),
          DailyMetrics(start.plusDays(6), 1),
        )),
        (Teams.Disability, Seq(
          DailyMetrics(start, 1),
          DailyMetrics(start.plusDays(1), 2),
          DailyMetrics(start.plusDays(2), 1),
          DailyMetrics(start.plusDays(3), 2),
          DailyMetrics(start.plusDays(4), 2),
          DailyMetrics(start.plusDays(5), 2),
          DailyMetrics(start.plusDays(6), 1),
        )),
        (Teams.MentalHealth, Seq(
          DailyMetrics(start, 1),
          DailyMetrics(start.plusDays(1), 0),
          DailyMetrics(start.plusDays(2), 0),
          DailyMetrics(start.plusDays(3), 1),
          DailyMetrics(start.plusDays(4), 0),
          DailyMetrics(start.plusDays(5), 0),
          DailyMetrics(start.plusDays(6), 0),
        )),
        (Teams.WellbeingSupport, Seq(
          DailyMetrics(start, 0),
          DailyMetrics(start.plusDays(1), 2),
          DailyMetrics(start.plusDays(2), 0),
          DailyMetrics(start.plusDays(3), 0),
          DailyMetrics(start.plusDays(4), 0),
          DailyMetrics(start.plusDays(5), 0),
          DailyMetrics(start.plusDays(6), 0),
        ))
      )

      val expectedCsv = Seq(
        Seq("Date", "Counselling Service", "Disability Services", "Mental Health Team", "Wellbeing Support"),
        Seq("2018-10-01", "0", "1", "1", "0"),
        Seq("2018-10-02", "0", "2", "0", "2"),
        Seq("2018-10-03", "1", "1", "0", "0"),
        Seq("2018-10-04", "2", "2", "1", "0"),
        Seq("2018-10-05", "1", "2", "0", "0"),
        Seq("2018-10-06", "1", "2", "0", "0"),
        Seq("2018-10-07", "1", "1", "0", "0")
      )

      ReportsController.csvFromMetrics(dateRange, metrics) mustBe expectedCsv
    }
  }
}
