package system

import java.net.URLEncoder
import java.time.{Duration, LocalDate, OffsetDateTime, ZoneId}

import play.api.mvc.{PathBindable, QueryStringBindable}
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

object Binders {
  implicit val universityIDPathBindable: PathBindable[UniversityID] =
    PathBindable.bindableString.transform(
      UniversityID.apply,
      _.string
    )

  implicit val universityIDQueryStringBindable: QueryStringBindable[UniversityID] =
    QueryStringBindable.bindableString.transform(
      UniversityID.apply,
      _.string
    )

  implicit object localDateQueryStringBindable extends QueryStringBindable.Parsing[LocalDate](
    LocalDate.parse(_, JavaTime.localDateFormat),
    (d: LocalDate) => URLEncoder.encode(Option(d).map(_.format(JavaTime.localDateFormat)).getOrElse(""), "utf-8"),
    (key: String, e: Exception) => "Cannot parse parameter %s as LocalDate: %s".format(key, e.getMessage)
  )

  implicit object offsetDateTimeQueryStringBindable extends QueryStringBindable.Parsing[OffsetDateTime](
    OffsetDateTime.parse(_, JavaTime.iSO8601DateFormat),
    (d: OffsetDateTime) => URLEncoder.encode(Option(d).map(_.format(JavaTime.iSO8601DateFormat)).getOrElse(""), "utf-8"),
    (key: String, e: Exception) => "Cannot parse parameter %s as OffsetDateTime: %s".format(key, e.getMessage)
  )

  implicit object zoneIdQueryStringBindable extends QueryStringBindable.Parsing[ZoneId](
    ZoneId.of,
    (z: ZoneId) => URLEncoder.encode(Option(z).map(_.getId).getOrElse(""), "utf-8"),
    (key: String, e: Exception) => "Cannot parse parameter %s as ZoneId: %s".format(key, e.getMessage)
  )

  implicit val durationQueryStringBindable: QueryStringBindable[Duration] =
    QueryStringBindable.bindableLong.transform(
      Duration.ofSeconds,
      _.getSeconds
    )
}
