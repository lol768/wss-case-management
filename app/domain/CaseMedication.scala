package domain

import enumeratum.{Enum, EnumEntry}
import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsPath, JsString}

import scala.collection.immutable

sealed abstract class CaseMedication(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}

object CaseMedication extends PlayEnumWithOther[CaseMedication] {
  case object Antidepressant extends CaseMedication("Antidepressant")
  case object Antipsychotic extends CaseMedication("Antipsychotic")
  case object Anxiolytic extends CaseMedication("Anxiolytic")
  case object Benzodiazepine extends CaseMedication("Benzodiazepine")
  case object BetaBlocker extends CaseMedication("Beta blocker")
  case object Hypnotic extends CaseMedication("Hypnotic")
  case object MoodStabiliser extends CaseMedication("Mood stabiliser")
  case object Stimulant extends CaseMedication("Stimulant")

  case class Other(override val value: Option[String]) extends CaseMedication("Other") with EnumEntryOther {
    override val label: String = "Other"
    override val description: String = s"$label (${value.orNull})"
  }

  override def nonOtherValues: immutable.IndexedSeq[CaseMedication] = findValues

  override def otherBuilder[O <: EnumEntryOther](otherValue: Option[String]): O = Other(otherValue).asInstanceOf[O]
}
