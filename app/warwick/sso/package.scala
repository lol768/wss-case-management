package warwick

import play.api.mvc.{PathBindable, QueryStringBindable}

package object sso {
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
}
