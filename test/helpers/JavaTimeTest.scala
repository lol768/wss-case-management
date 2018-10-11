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

  private def offsetDateTime(parse: String): OffsetDateTime =
    LocalDateTime.parse(parse).atZone(ZoneId.systemDefault).toOffsetDateTime


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

    "handle LocalDate yesterday" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 15)) must be ("Thu 15 Mar")
      }
    }

    "handle LocalDate this week" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(LocalDate.of(2018, 3, 18)) must be ("Sun 18 Mar")
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

    "handle OffsetDateTime today" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-16T11:13:14.000")) must be ("Today 11:13")
      }
    }

    "handle OffsetDateTime today without Today" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-16T11:13:14.000"), printToday = false) must be ("11:13")
      }
    }

    "handle OffsetDateTime yesterday" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-15T11:13:14.000")) must be ("Thu 15 Mar, 11:13")
      }
    }

    "handle OffsetDateTime this week" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-18T11:13:14.000")) must be ("Sun 18 Mar, 11:13")
      }
    }

    "handle OffsetDateTime only weekday" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-21T11:13:14.000"), onlyWeekday = true) must be ("Wed 21 Mar, 11:13")
      }
    }

    "handle OffsetDateTime this year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2018-03-03T11:13:14.000")) must be ("Sat 3 Mar, 11:13")
      }
    }

    "handle OffsetDateTime last year" in {
      JavaTimeTest.withMockDateTime(now.toInstant) {
        JavaTime.Relative(offsetDateTime("2017-03-03T11:13:14.000")) must be ("Fri 3 Mar 2017, 11:13")
      }
    }

  }

}
