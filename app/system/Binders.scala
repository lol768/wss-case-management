package system

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

  implicit val localDatePathBindable: PathBindable[LocalDate] =
    PathBindable.bindableString.transform(
      LocalDate.parse(_, JavaTime.localDateFormat),
      _.format(JavaTime.localDateFormat)
    )

  implicit val offsetDateTimeQueryStringBindable: QueryStringBindable[OffsetDateTime] =
    QueryStringBindable.bindableString.transform(
      OffsetDateTime.parse(_, JavaTime.iSO8601DateFormat),
      _.format(JavaTime.iSO8601DateFormat)
    )

  implicit val zoneIdQueryStringBindable: QueryStringBindable[ZoneId] =
    QueryStringBindable.bindableString.transform(
      ZoneId.of,
      _.getId
    )

  implicit val durationQueryStringBindable: QueryStringBindable[Duration] =
    QueryStringBindable.bindableLong.transform(
      Duration.ofSeconds,
      _.getSeconds
    )
}
