package domain

import java.net.URLEncoder

import enumeratum.{Enum, EnumEntry}
import helpers.StringUtils._
import play.api.data.format.Formats.parsing
import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.collection.immutable

case class IssueKey(keyType: IssueKeyType, number: Int) {
  final val string: String = f"${keyType.code}%s-$number%03d"
}

object IssueKey {
  def apply(in: String): IssueKey = in match {
    case r"""([A-Za-z])${prefixStr}[A-Za-z]*\-?0*(\d+)${numberStr}""" =>
      IssueKey(IssueKeyType.withPrefix(prefixStr.toUpperCase.charAt(0)), numberStr.toInt)

    case _ => throw new IllegalArgumentException("Invalid IssueKey format")
  }

  implicit object issueKeyPathBindable extends PathBindable.Parsing[IssueKey](
    IssueKey.apply,
    _.string,
    (key: String, e: Exception) => "Cannot parse parameter %s as IssueKey: %s".format(key, e.getMessage)
  )

  implicit object issueKeyQueryStringBindable extends QueryStringBindable.Parsing[IssueKey](
    IssueKey.apply,
    (k: IssueKey) => URLEncoder.encode(Option(k).map(_.string).getOrElse(""), "utf-8"),
    (key: String, e: Exception) => "Cannot parse parameter %s as IssueKey: %s".format(key, e.getMessage)
  )

  implicit val issueKeyFormatter: Formatter[IssueKey] = new Formatter[IssueKey] {
    override val format: Option[(String, Seq[Any])] = Some(("format.issueKey", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], IssueKey] =
      parsing(IssueKey.apply, "error.issueKey", Nil)(key, data)

    override def unbind(key: String, value: IssueKey) = Map(key -> value.string)
  }

  val formField: Mapping[IssueKey] = Forms.of(issueKeyFormatter)
}

sealed abstract class IssueKeyType(val code: String, val prefix: Char) extends EnumEntry
object IssueKeyType extends Enum[IssueKeyType] {
  case object Case extends IssueKeyType("CAS", 'C')
  case object MigratedCase extends IssueKeyType("MIG", 'M')
  case object Enquiry extends IssueKeyType("ENQ", 'E')
  case object Appointment extends IssueKeyType("APP", 'A')

  override def values: immutable.IndexedSeq[IssueKeyType] = findValues

  def withPrefix(prefix: Char): IssueKeyType =
    values.find(_.prefix == prefix).getOrElse(
      throw new NoSuchElementException(s"$prefix is not a valid IssueKeyType prefix (${values.map(_.prefix).mkString(", ")})")
    )
}
