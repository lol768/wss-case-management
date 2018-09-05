package domain

import java.time.OffsetDateTime

import domain.dao.CaseDao.Case
import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class CaseTag(description: String) extends EnumEntry
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