package domain

import enumeratum.EnumEntry

import scala.collection.immutable

sealed abstract class StudentSupportIssueType(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object StudentSupportIssueType extends PlayEnumWithOther[StudentSupportIssueType] {
  case object Academic extends StudentSupportIssueType("Academic")
  case object Accident extends StudentSupportIssueType("Accident")
  case object Accommodation extends StudentSupportIssueType("Accommodation")
  case object AlcoholRelated extends StudentSupportIssueType("Alcohol-related")
  case object Assault extends StudentSupportIssueType("Assault")
  case object Complaint extends StudentSupportIssueType("Complaint")
  case object CrimeSecurity extends StudentSupportIssueType("Crime/Security")
  case object Damage extends StudentSupportIssueType("Damage")
  case object Emotional extends StudentSupportIssueType("Emotional")
  case object Family extends StudentSupportIssueType("Family")
  case object Financial extends StudentSupportIssueType("Financial")
  case object Harassment extends StudentSupportIssueType("Harassment")
  case object HealthIllness extends StudentSupportIssueType("Health/Illness")
  case object IllegalDrugs extends StudentSupportIssueType("Illegal drugs")
  case object Illness extends StudentSupportIssueType("Illness")
  case object MentalHealth extends StudentSupportIssueType("Mental health")
  case object Prevent extends StudentSupportIssueType("Prevent")
  case object SafeAndWellCheck extends StudentSupportIssueType("Safe and Well check")
  case object SelfHarm extends StudentSupportIssueType("Self harm")
  case object SexualAssault extends StudentSupportIssueType("Sexual assault")
  case object Smoking extends StudentSupportIssueType("Smoking")
  case object UnauthorisedGuests extends StudentSupportIssueType("Unauthorised Guests/Party")

  case class Other(override val value: Option[String]) extends StudentSupportIssueType("Other welfare") with EnumEntryOther {
    override val label: String = "Other welfare"
    override val description: String = s"$label (${value.orNull})"
  }

  override def nonOtherValues: immutable.IndexedSeq[StudentSupportIssueType] = findValues

  override def otherBuilder[O <: EnumEntryOther](otherValue: Option[String]): O = Other(otherValue).asInstanceOf[O]
}
