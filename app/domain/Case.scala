package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

object CaseStuff {

  /**
    * This might not be a way we should do things, but if we did want a service to return
    * everything we need to display
    */
  case class FullyJoined(
    clientCase: domain.dao.CaseDao.Case,
    tags: Set[CaseTag],
//    notes: Seq[CaseNote],
//    attachments: Seq[UploadedDocument],
//    relatedAppointments: Seq[Appointment],
    relatedCases: Seq[domain.dao.CaseDao.Case]
  )
}

sealed abstract class CaseTag(description: String) extends EnumEntry
object CaseTag extends PlayEnum[CaseTag] {
  object Accommodation extends CaseTag("Accommodation")
  object Alcohol extends CaseTag("Alcohol related")
  object Antisocial extends CaseTag("Anti-social behaviour")
  object Bullying extends CaseTag("Bullying")
  object Burglary extends CaseTag("Burglary / Break In")
  object Disability extends CaseTag("Disability")
  object DomesticViolence extends CaseTag("Domestic Violence")
  object Drugs extends CaseTag("Drugs")
  object HomeSickness extends CaseTag("Home Sickness")
  object LegalHighs extends CaseTag("Legal Highs")
  object MentalHealth  extends CaseTag("Mental Health Issue")
  object Racism extends CaseTag("Racism")
  object SexualAssault extends CaseTag("Sexual Assault")
  val values: immutable.IndexedSeq[CaseTag] = findValues
}

sealed trait EmergencyService extends EnumEntry
object EmergencyService extends PlayEnum[EmergencyService] {
  object Police extends EmergencyService
  object Ambulance extends EmergencyService
  object Fire extends EmergencyService
  val values: immutable.IndexedSeq[EmergencyService] = findValues
}

sealed abstract class CaseType(val applicableTo: Seq[Team]) extends EnumEntry
abstract class MentalHealthCaseType extends CaseType(Seq(Teams.MentalHealth))
object CaseType extends PlayEnum[CaseType] {
  object MentalHealthAssessment extends MentalHealthCaseType
  object MentalHealthCrisis extends MentalHealthCaseType
  object MentalHealthWellbeing extends MentalHealthCaseType
  object MentalHealthMentoring extends MentalHealthCaseType

  override def values: immutable.IndexedSeq[CaseType] = findValues

  def valuesFor(team: Team): Seq[CaseType] = values.filter { t =>
    t.applicableTo.contains(team)
  }
}

sealed trait CaseCause extends EnumEntry
object CaseCause extends PlayEnum[CaseCause] {
  object New extends CaseCause
  object Recurring extends CaseCause
  object Ongoing extends CaseCause
  object Referred extends CaseCause
  object SelfReferred extends CaseCause

  override def values: immutable.IndexedSeq[CaseCause] = findValues
}