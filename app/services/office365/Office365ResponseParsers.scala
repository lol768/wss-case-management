package services.office365

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import play.api.libs.functional.syntax._
import play.api.libs.json._
import services.FreeBusyService.FreeBusyStatus

object Office365ResponseParsers {
  case class CalendarEvent(
    start: ZonedDateTime,
    end: ZonedDateTime,
    allDay: Boolean,
    freeBusyStatus: FreeBusyStatus,
    categories: Seq[String]
  )
  val calendarEventReads: Reads[CalendarEvent] = (
    (__ \ "Start" \ "DateTime").read[LocalDateTime].map(_.atZone(ZoneId.systemDefault)) and
    (__ \ "End" \ "DateTime").read[LocalDateTime].map(_.atZone(ZoneId.systemDefault)) and
    (__ \ "IsAllDay").read[Boolean] and
    (__ \ "ShowAs").readNullable[String].map(_.map(_.toLowerCase) match {
      case Some("tentative") | Some("1") => FreeBusyStatus.Tentative
      case Some("busy") | Some("2") | Some("unknown") | Some("-1") => FreeBusyStatus.Busy
      case Some("oof") | Some("3") => FreeBusyStatus.OutOfOffice
      case Some("workingelsewhere") | Some("4") => FreeBusyStatus.WorkingElsewhere
      case _ => FreeBusyStatus.Free
    }) and
    (__ \ "Categories").read[Seq[String]]
  )(CalendarEvent.apply _)

  val calendarEventsReads: Reads[Seq[CalendarEvent]] = (__ \ "value").read[Seq[CalendarEvent]](Reads.seq(calendarEventReads))

  def validateAPIResponse[A](jsValue: JsValue, parser: Reads[A]): JsResult[A] =
    jsValue.validate[A](parser)
}
