package helpers

import java.time._

import uk.ac.warwick.util.core.DateTimeUtils

trait MockJavaTime {

  // ♫ We're gonna mock around the Clock tonight ♬
  def withMockDateTime(mockDateTime: Instant)(fn: => Unit): Unit = {
    DateTimeUtils.useMockDateTime(mockDateTime, () => fn)
  }

}
