package helpers

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

import play.api.libs.json._
import uk.ac.warwick.util.core.DateTimeUtils

object JavaTime {
  val timeZone: ZoneId = ZoneId.systemDefault()

  val localDateFormat: DateTimeFormatter = DateTimeFormatter.ISO_DATE
  val iSO8601DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(timeZone)

  implicit def dateTimeOrdering: Ordering[OffsetDateTime] = Ordering.fromLessThan(_.isBefore(_))
  implicit def localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_.isBefore(_))

  implicit val localDateWrites: Writes[LocalDate] = (d: LocalDate) => JsString(localDateFormat.format(d))
  implicit val offsetDateTimeISOWrites: Writes[OffsetDateTime] = (d: OffsetDateTime) => JsString(JavaTime.iSO8601DateFormat.format(d))
  implicit val offsetDateTimeEpochMilliReads: Reads[OffsetDateTime] =
    (json: JsValue) => json.validate[Long].map(millis => OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone))
  implicit val offsetDateTimeEpochMilliWrites: Writes[OffsetDateTime] = (d: OffsetDateTime) => JsNumber(d.toInstant.toEpochMilli)

  def clock: Clock = DateTimeUtils.CLOCK_IMPLEMENTATION

  // Wrappers for now() that use the mockable clock
  def offsetDateTime: OffsetDateTime = OffsetDateTime.now(clock)
  def localDateTime: LocalDateTime = LocalDateTime.now(clock)
  def localDate: LocalDate = LocalDate.now(clock)
  def localTime: LocalTime = LocalTime.now(clock)
  def instant: Instant = Instant.now(clock)

  object Relative {

    private val dateWeekdayFormatter = DateTimeFormatter.ofPattern("EEE")
    private val dateFullWithoutYearFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dateFullFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

    private val onlyTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val todayTimeFormatter = DateTimeFormatter.ofPattern("'Today' HH:mm")
    private val yesterdayTimeFormatter = DateTimeFormatter.ofPattern("'Yesterday' HH:mm")
    private val weekdayTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    private val dateTimeFullWithoutYearFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    private val dateTimeFullFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")

    def apply(date: LocalDate): String = {
      val now = LocalDate.now(clock)

      if (date.isEqual(now)) {
        "today"
      } else if (date.isEqual(now.plusDays(1))) {
        "tomorrow"
      } else {
        val yesterday = now.minusDays(1)

        if (date.get(WeekFields.ISO.weekOfWeekBasedYear) == yesterday.get(WeekFields.ISO.weekOfWeekBasedYear)) {
          dateWeekdayFormatter.format(date)
        } else if (date.getYear == yesterday.getYear) {
          dateFullWithoutYearFormatter.format(date)
        } else {
          dateFullFormatter.format(date)
        }
      }

    }

    def apply(date: OffsetDateTime, printToday: Boolean = false, onlyWeekday: Boolean = false): String = {
      val now = offsetDateTime

      if (date.toLocalDate.isEqual(now.toLocalDate)) {
        if (printToday) {
          todayTimeFormatter.format(date)
        } else {
          onlyTimeFormatter.format(date)
        }
      } else if (date.toLocalDate.isEqual(now.toLocalDate.minusDays(1))) {
        yesterdayTimeFormatter.format(date)
      } else if (onlyWeekday || date.get(WeekFields.ISO.weekOfWeekBasedYear) == now.get(WeekFields.ISO.weekOfWeekBasedYear)) {
        weekdayTimeFormatter.format(date)
      } else if (date.getYear == now.getYear) {
        dateTimeFullWithoutYearFormatter.format(date)
      } else {
        dateTimeFullFormatter.format(date)
      }
    }

  }
}