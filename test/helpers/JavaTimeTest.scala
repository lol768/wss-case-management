package helpers

import java.time._

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.ac.warwick.util.core.DateTimeUtils

object JavaTimeTest {

  // ♫ We're gonna mock around the Clock tonight ♬
  def withMockDateTime(mockDateTime: Instant)(fn: => Unit): Unit = {
    DateTimeUtils.useMockDateTime(mockDateTime, () => fn)
  }

}

class JavaTimeTest extends PlaySpec with MockitoSugar {

  private val now = ZonedDateTime.of(2018, 3, 16, 12, 35, 15, 0, ZoneId.systemDefault) // It's a Friday

  "JavaTime.Relative" should {

    "handle LocalDate today" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 16)) must be ("today")
      }
    }

    "handle LocalDate tomorrow" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 17)) must be ("tomorrow")
      }
    }

    "handle LocalDate this week" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 18)) must be ("Sun")
      }
    }

    "handle LocalDate this year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 3)) must be ("Sat 3 Mar")
      }
    }

    "handle LocalDate last year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2017, 3, 3)) must be ("Fri 3 Mar 2017")
      }
    }

    "handle ZonedDateTime today" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 16).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault)) must be ("11:13")
      }
    }

    "handle ZonedDateTime today with Today" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 16).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault), printToday = true) must be ("Today 11:13")
      }
    }

    "handle ZonedDateTime yesterday" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 15).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault)) must be ("Yesterday 11:13")
      }
    }

    "handle ZonedDateTime this week" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 18).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault)) must be ("Sun 11:13")
      }
    }

    "handle ZonedDateTime only weekday" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 21).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault), onlyWeekday = true) must be ("Wed 11:13")
      }
    }

    "handle ZonedDateTime this year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 3).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault)) must be ("Sat 3 Mar, 11:13")
      }
    }

    "handle ZonedDateTime last year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2017, 3, 3).atTime(11, 13, 14, 15).atZone(ZoneId.systemDefault)) must be ("Fri 3 Mar 2017, 11:13")
      }
    }

  }

}
