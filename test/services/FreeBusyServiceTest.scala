package services

import java.time.{LocalDate, LocalTime, Month}

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.FreeBusyService.{FreeBusyPeriod, FreeBusyStatus}
import warwick.core.helpers.JavaTime

class FreeBusyServiceTest extends PlaySpec with MockitoSugar with ScalaFutures {

  "FreeBusyPeriod" should {
    "combine adjacent/overlapping periods of the same type into a single period" in {
      val monday = LocalDate.of(2018, Month.OCTOBER, 1)
      val tuesday = LocalDate.of(2018, Month.OCTOBER, 2)
      val wednesday = LocalDate.of(2018, Month.OCTOBER, 3)

      val nineAM = LocalTime.of(9, 0)
      val nineThirtyAM = LocalTime.of(9, 30)
      val tenAM = LocalTime.of(10, 0)
      val elevenAM = LocalTime.of(11, 0)
      val midday = LocalTime.of(12, 0)
      val onePM = LocalTime.of(13, 0)
      val twoPM = LocalTime.of(14, 0)
      val threePM = LocalTime.of(15, 0)

      val allPeriods = Seq(
        // Monday 9am - 10am Busy
        FreeBusyPeriod(start = monday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(tenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Monday 9am - 11am Busy
        FreeBusyPeriod(start = monday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Monday 11am - 3pm working elsewhere
        FreeBusyPeriod(start = monday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(threePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.WorkingElsewhere),
        // Monday 2pm - 3pm Busy
        FreeBusyPeriod(start = monday.atTime(twoPM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(threePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Tuesday 9am - 2pm Out of Office
        FreeBusyPeriod(start = tuesday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = tuesday.atTime(twoPM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.OutOfOffice),
        // Tuesday 10am - 11am Out of Office
        FreeBusyPeriod(start = tuesday.atTime(tenAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = tuesday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.OutOfOffice),
        // Tuesday midday - 1pm Busy
        FreeBusyPeriod(start = tuesday.atTime(midday).atZone(JavaTime.timeZone).toOffsetDateTime, end = tuesday.atTime(onePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Wednesday 9am - 10am Busy
        FreeBusyPeriod(start = wednesday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = wednesday.atTime(tenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Wednesday 9:30am - 10am Busy
        FreeBusyPeriod(start = wednesday.atTime(nineThirtyAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = wednesday.atTime(tenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Wednesday 9:30am - 11am Busy
        FreeBusyPeriod(start = wednesday.atTime(nineThirtyAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = wednesday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy)
      )

      FreeBusyPeriod.combine(allPeriods) mustBe Seq(
        // Monday 9am - 11am Busy
        FreeBusyPeriod(start = monday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Monday 11am - 3pm working elsewhere
        FreeBusyPeriod(start = monday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(threePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.WorkingElsewhere),
        // Monday 2pm - 3pm Busy
        FreeBusyPeriod(start = monday.atTime(twoPM).atZone(JavaTime.timeZone).toOffsetDateTime, end = monday.atTime(threePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Tuesday 9am - 2pm Out of Office
        FreeBusyPeriod(start = tuesday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = tuesday.atTime(twoPM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.OutOfOffice),
        // Tuesday midday - 1pm Busy
        FreeBusyPeriod(start = tuesday.atTime(midday).atZone(JavaTime.timeZone).toOffsetDateTime, end = tuesday.atTime(onePM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy),
        // Wednesday 9am - 11am Busy
        FreeBusyPeriod(start = wednesday.atTime(nineAM).atZone(JavaTime.timeZone).toOffsetDateTime, end = wednesday.atTime(elevenAM).atZone(JavaTime.timeZone).toOffsetDateTime, status = FreeBusyStatus.Busy)
      )
    }
  }

}
