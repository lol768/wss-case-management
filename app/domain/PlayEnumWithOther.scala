package domain

import enumeratum.{EnumEntry, PlayEnum}
import play.api.data.Mapping
import play.api.data.Forms._
import helpers.StringUtils._
import scala.collection.immutable

object EnumEntryOther {
  val id = "Other"
  val entryName = "Other"
}

trait EnumEntryOther extends EnumEntry with IdAndDescription {
  val value: Option[String]
  val label: String

  override val id: String = EnumEntryOther.id
  override val entryName: String = EnumEntryOther.entryName
  // Add the line below to any implementing classes
  // override val description: String = s"$label (${value.orNull})"
}

trait PlayEnumWithOther[A <: EnumEntry] extends PlayEnum[A] {
  def otherBuilder[O <: EnumEntryOther](otherValue: Option[String]): O

  def apply(entryNames: List[String], otherValue: Option[String]): Set[A] =
    entryNames.map(withName).map {
      case _: EnumEntryOther => otherBuilder(otherValue)
      case entry => entry
    }.toSet

  def otherValue(entries: Set[A]): Option[String] =
    entries.collectFirst { case entry: EnumEntryOther => entry.value }.flatten

  def nonOtherValues: immutable.IndexedSeq[A]

  override final def values: immutable.IndexedSeq[A] = nonOtherValues :+ otherBuilder(None)

  val formMapping: Mapping[Set[A]] = mapping(
    "entries" -> set(formField),
    "otherValue" -> optional(text)
  )(
    (entries, otherValue) => entries.map {
      case _: EnumEntryOther => otherBuilder(otherValue)
      case entry => entry
    }
  )(
    entries => Option((entries, otherValue(entries)))
  ).verifying("error.enumWithOther.otherValue.empty", _.forall {
    case entry: EnumEntryOther => entry.value.exists(_.hasText)
    case _ => true
  })
}
