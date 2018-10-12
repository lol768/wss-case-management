package system

import java.time.{Duration, OffsetDateTime, ZoneId}

import helpers.JavaTime
import play.api.mvc.{PathBindable, QueryStringBindable}
import warwick.sso.UniversityID

object Binders {
  implicit val universityIDPathBindable: PathBindable[UniversityID] = new PathBindable[UniversityID] {
    override def bind(key: String, value: String): Either[String, UniversityID] =
      Right(UniversityID(value))

    override def unbind(key: String, value: UniversityID): String =
      value.string
  }

  implicit val universityIDQueryStringBindable: QueryStringBindable[UniversityID] = new QueryStringBindable[UniversityID] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UniversityID]] =
      params.get(key).flatMap(_.headOption).map { p => Right(UniversityID(p)) }

    override def unbind(key: String, value: UniversityID): String =
      s"$key=${value.string}"
  }

  implicit val offsetDateTimeQueryStringBindable: QueryStringBindable[OffsetDateTime] = new QueryStringBindable[OffsetDateTime] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, OffsetDateTime]] =
      params.get(key).flatMap(_.headOption).map { p => Right(OffsetDateTime.parse(p, JavaTime.iSO8601DateFormat)) }

    override def unbind(key: String, value: OffsetDateTime): String =
      s"$key=${value.format(JavaTime.iSO8601DateFormat)}"
  }

  implicit val zoneIdQueryStringBindable: QueryStringBindable[ZoneId] = new QueryStringBindable[ZoneId] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ZoneId]] =
      params.get(key).flatMap(_.headOption).map { p => Right(ZoneId.of(p)) }

    override def unbind(key: String, value: ZoneId): String =
      s"$key=${value.getId}"
  }

  implicit val durationQueryStringBindable: QueryStringBindable[Duration] = new QueryStringBindable[Duration] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Duration]] =
      params.get(key).flatMap(_.headOption).map { p => Right(Duration.ofSeconds(p.toLong)) }

    override def unbind(key: String, value: Duration): String =
      s"$key=${value.getSeconds}"
  }
}
