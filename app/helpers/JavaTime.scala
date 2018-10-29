package helpers

import java.time._
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.util.Locale

import play.api.data.format.{Formats, Formatter}
import play.api.data.{FormError, Forms, Mapping}
import play.api.libs.json._
import uk.ac.warwick.util.core.DateTimeUtils

import scala.collection.JavaConverters._

object JavaTime {
  val timeZone: ZoneId = ZoneId.systemDefault()

  val localDateFormat: DateTimeFormatter = DateTimeFormatter.ISO_DATE
  val iSO8601DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(timeZone)

  private val ordinalLookup: Map[java.lang.Long, String] =
    (1 to 31).map { d =>
      val ordinal = d % 10 match {
        case 1 if d != 11 => "st"
        case 2 if d != 12 => "nd"
        case 3 if d != 13 => "rd"
        case _ => "th"
      }

      (d.toLong: java.lang.Long) -> s"$d$ordinal"
    }.toMap

  val dateFullNoDayFormatter: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendText(ChronoField.DAY_OF_MONTH, ordinalLookup.asJava)
      .appendLiteral(' ')
      .appendPattern("MMM")
      .appendLiteral(' ')
      .appendPattern("yyyy")
      .toFormatter(Locale.UK)

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

  object OffsetDateTimeFormatter extends Formatter[OffsetDateTime] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OffsetDateTime] =
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[OffsetDateTime]
          .either(OffsetDateTime.parse(s, iSO8601DateFormat))
          .left.map(_ => Seq(FormError(key, "error.date", Nil)))
      }

    override def unbind(key: String, value: OffsetDateTime): Map[String, String] =
      Map(key -> value.format(iSO8601DateFormat))
  }

  val offsetDateTimeFormField: Mapping[OffsetDateTime] = Forms.of(OffsetDateTimeFormatter)

  object Relative {

    private val dateFullWithoutYearFormatter =
      new DateTimeFormatterBuilder()
        .appendPattern("EEE")
        .appendLiteral(' ')
        .appendText(ChronoField.DAY_OF_MONTH, ordinalLookup.asJava)
        .appendLiteral(' ')
        .appendPattern("MMM")
        .toFormatter(Locale.UK)

    private val dateFullFormatter =
      new DateTimeFormatterBuilder()
        .append(dateFullWithoutYearFormatter)
        .appendLiteral(' ')
        .appendPattern("yyyy")
        .toFormatter(Locale.UK)

    private val onlyTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val dateTimeFullWithoutYearFormatter =
      new DateTimeFormatterBuilder()
        .append(dateFullWithoutYearFormatter)
        .appendLiteral(", ")
        .append(onlyTimeFormatter)
        .toFormatter(Locale.UK)

    private val dateTimeFullFormatter =
      new DateTimeFormatterBuilder()
        .append(dateFullFormatter)
        .appendLiteral(", ")
        .append(onlyTimeFormatter)
        .toFormatter(Locale.UK)

    def apply(date: LocalDate): String = {
      val now = localDate

      if (date.isEqual(now)) {
        "today"
      } else if (date.isEqual(now.plusDays(1))) {
        "tomorrow"
      } else {
        val yesterday = now.minusDays(1)

        if (date.getYear == yesterday.getYear) {
          dateFullWithoutYearFormatter.format(date)
        } else {
          dateFullFormatter.format(date)
        }
      }

    }

    def apply(date: OffsetDateTime, printToday: Boolean = true, lowercaseToday: Boolean = false): String = {
      val now = offsetDateTime

      if (date.toLocalDate.isEqual(now.toLocalDate)) {
        if (printToday) {
          "%s%s".format(
            if (lowercaseToday) "today " else "Today ",
            onlyTimeFormatter.format(date)
          )
        } else {
          onlyTimeFormatter.format(date)
        }
      } else if (date.getYear == now.getYear) {
        dateTimeFullWithoutYearFormatter.format(date)
      } else {
        dateTimeFullFormatter.format(date)
      }
    }

  }
}