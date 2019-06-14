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
          DailyMetrics(start.plusDays(1L), 0),
          DailyMetrics(start.plusDays(2L), 1),
          DailyMetrics(start.plusDays(3L), 2),
          DailyMetrics(start.plusDays(4L), 1),
          DailyMetrics(start.plusDays(5L), 1),
          DailyMetrics(start.plusDays(6L), 1),
        )),
        (Teams.Disability, Seq(
          DailyMetrics(start, 1),
          DailyMetrics(start.plusDays(1L), 2),
          DailyMetrics(start.plusDays(2L), 1),
          DailyMetrics(start.plusDays(3L), 2),
          DailyMetrics(start.plusDays(4L), 2),
          DailyMetrics(start.plusDays(5L), 2),
          DailyMetrics(start.plusDays(6L), 1),
        )),
        (Teams.MentalHealth, Seq(
          DailyMetrics(start, 1),
          DailyMetrics(start.plusDays(1L), 0),
          DailyMetrics(start.plusDays(2L), 0),
          DailyMetrics(start.plusDays(3L), 1),
          DailyMetrics(start.plusDays(4L), 0),
          DailyMetrics(start.plusDays(5L), 0),
          DailyMetrics(start.plusDays(6L), 0),
        )),
        (Teams.WellbeingSupport, Seq(
          DailyMetrics(start, 0),
          DailyMetrics(start.plusDays(1L), 2),
          DailyMetrics(start.plusDays(2L), 0),
          DailyMetrics(start.plusDays(3L), 0),
          DailyMetrics(start.plusDays(4L), 0),
          DailyMetrics(start.plusDays(5L), 0),
          DailyMetrics(start.plusDays(6L), 0),
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
