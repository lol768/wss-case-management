package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.dao.CaseDao.Case
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import warwick.sso.Usercode

import scala.collection.immutable

sealed abstract class CaseTag(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = entryName
}
object CaseTag extends PlayEnum[CaseTag] {
  case object Accommodation extends CaseTag("Accommodation")
  case object Alcohol extends CaseTag("Alcohol related")
  case object Antisocial extends CaseTag("Anti-social behaviour")
  case object Bullying extends CaseTag("Bullying")
  case object Burglary extends CaseTag("Burglary / Break In")
  case object Disability extends CaseTag("Disability")
  case object DomesticViolence extends CaseTag("Domestic Violence")
  case object Drugs extends CaseTag("Drugs")
  case object HomeSickness extends CaseTag("Home Sickness")
  case object LegalHighs extends CaseTag("Legal Highs")
  case object MentalHealth  extends CaseTag("Mental Health Issue")
  case object Racism extends CaseTag("Racism")
  case object SexualAssault extends CaseTag("Sexual Assault")
  val values: immutable.IndexedSeq[CaseTag] = findValues
}

sealed abstract class CaseType(val description: String, val applicableTo: Seq[Team]) extends EnumEntry
abstract class MentalHealthCaseType(description: String) extends CaseType(description, Seq(Teams.MentalHealth))
object CaseType extends PlayEnum[CaseType] {
  case object MentalHealthAssessment extends MentalHealthCaseType("Mental Health Assessment")
  case object MentalHealthCrisis extends MentalHealthCaseType("Mental Health Crisis")
  case object MentalHealthWellbeing extends MentalHealthCaseType("Mental Health Mentoring")
  case object MentalHealthMentoring extends MentalHealthCaseType("Mental Health Wellbeing")

  override def values: immutable.IndexedSeq[CaseType] = findValues

  def valuesFor(team: Team): Seq[CaseType] = values.filter { t =>
    t.applicableTo.contains(team)
  }
}

sealed abstract class CaseCause(val description: String) extends EnumEntry
object CaseCause extends PlayEnum[CaseCause] {
  case object New extends CaseCause("New issue")
  case object Recurring extends CaseCause("Recurring issue")
  case object Ongoing extends CaseCause("Ongoing issue")
  case object Referred extends CaseCause("Referred")
  case object SelfReferred extends CaseCause("Self-referred")

  override def values: immutable.IndexedSeq[CaseCause] = findValues
}

case class CaseLink(
  linkType: CaseLinkType,
  outgoing: Case,
  incoming: Case,
  updatedDate: OffsetDateTime
)

sealed abstract class CaseLinkType(val outwardDescription: String, val inwardDescription: String) extends EnumEntry
object CaseLinkType extends PlayEnum[CaseLinkType] {
  case object Related extends CaseLinkType("is related to", "is related to")
  case object Merge extends CaseLinkType("merged to", "merged from")

  override def values: immutable.IndexedSeq[CaseLinkType] = findValues
}

case class CaseNote(
  id: UUID,
  noteType: CaseNoteType,
  text: String,
  teamMember: Usercode,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

object CaseNote {
  // oldest first
  val dateOrdering: Ordering[CaseNote] = Ordering.by[CaseNote, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

/**
  * Just the data of a case note required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class CaseNoteSave(
  text: String,
  teamMember: Usercode
)

sealed abstract class CaseNoteType(val description: String) extends EnumEntry
object CaseNoteType extends PlayEnum[CaseNoteType] {
  case object AppointmentNote extends CaseNoteType("Appointment note")
  case object AssociatedCase extends CaseNoteType("Associated case")
  case object CaseClosed extends CaseNoteType("Case closed")
  case object CaseReopened extends CaseNoteType("Case reopened")
  case object GeneralNote extends CaseNoteType("General note")
  case object Referral extends CaseNoteType("Referral")

  override def values: immutable.IndexedSeq[CaseNoteType] = findValues
}

case class CaseDocument(
  id: UUID,
  documentType: CaseDocumentType,
  file: UploadedFile,
  teamMember: Usercode,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

/**
  * Just the metadata of the document required to save it
  */
case class CaseDocumentSave(
  documentType: CaseDocumentType,
  teamMember: Usercode
)

sealed abstract class CaseDocumentType(val description: String) extends EnumEntry
object CaseDocumentType extends PlayEnum[CaseDocumentType] {
  case object DisabilityNeedsAssessmentReport extends CaseDocumentType("Disability Needs Assessment Report")
  case object DSAEntitlementLetter extends CaseDocumentType("DSA Entitlement Letter")
  case object MedicalEvidenceDocuments extends CaseDocumentType("Medical Evidence Documents")
  case object MitigatingCircumstancesForm extends CaseDocumentType("Mitigating Circumstances Form")
  case object SelectedEmails extends CaseDocumentType("Selected Emails")
  case object StudentSupportInternalDocuments extends CaseDocumentType("Student Support Internal Documents")
  case object SecurityReport extends CaseDocumentType("Security Report")
  case object UIRForm extends CaseDocumentType("UIR Form")
  case object PoliceIncidentDocument extends CaseDocumentType("Police Incident Document")
  case object SpecificLearningDifficultyDocument extends CaseDocumentType("Specific Learning Difficulty Document")
  case object StudentSupportInformationForm extends CaseDocumentType("Student Support Information Form")
  case object MentalHealthServicesOther extends CaseDocumentType("Mental Health Services - Other Documents")
  case object ReleaseOfInformationConsentForm extends CaseDocumentType("Release Of Information Consent Form")
  case object Photos extends CaseDocumentType("Photos")
  case object KeyLog extends CaseDocumentType("Key Log")

  override def values: immutable.IndexedSeq[CaseDocumentType] = findValues
}