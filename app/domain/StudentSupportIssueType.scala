package domain

import enumeratum.EnumEntry

import scala.collection.immutable

sealed abstract class StudentSupportIssueType(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object StudentSupportIssueType extends PlayEnumWithOther[StudentSupportIssueType] {
  case object Academic extends StudentSupportIssueType("Academic")

  case class Other(override val value: Option[String]) extends StudentSupportIssueType("Other welfare") with EnumEntryOther {
    override val label: String = "Other welfare"
  }

  override def nonOtherValues: immutable.IndexedSeq[StudentSupportIssueType] = findValues

  override def otherBuilder[O <: EnumEntryOther](otherValue: Option[String]): O = Other(otherValue).asInstanceOf[O]
}
