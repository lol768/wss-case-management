package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.dao.CaseDao.{Case, CaseClient, CaseClientVersion, CaseVersion, StoredCaseTag, StoredCaseTagVersion}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import play.api.libs.json.{JsValue, Json, Writes}
import services.tabula.ProfileService
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, User, UserLookupService, Usercode}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

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
  id: UUID,
  linkType: CaseLinkType,
  outgoing: Case,
  incoming: Case,
  caseNote: CaseNote,
  teamMember: Usercode,
  updatedDate: OffsetDateTime
) extends HasCreator

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
  case object DocumentNote extends CaseNoteType("Document added")
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
  caseNote: CaseNote,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
) extends HasCreator

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

object CaseHistory {

  val writer: Writes[CaseHistory] = (r: CaseHistory) => Json.obj(
    "subject" -> toJson(r.subject),
    "team" -> toJson(r.team)(Teams.writer),
    "state" -> toJson(r.state),
    "incidentDate" -> toJson(r.incidentDate.map { case (date, v, u) => (date.map(JavaTime.Relative.apply(_)).getOrElse("Not linked to incident"), v, u) }),
    "onCampus" -> toJson(r.onCampus.map { case (onCampus, v, u) => (onCampus.map(isOnCampus => if (isOnCampus) "On-campus" else "Off-campus").getOrElse("Not linked to incident"), v, u) }),
    "notifiedPolice" -> toJson(r.notifiedPolice.map { case (notifiedPolice, v, u) => (notifiedPolice.map(if (_) "Added Police notified" else "Removed Police notified").getOrElse("Not linked to incident"), v, u) }),
    "notifiedAmbulance" -> toJson(r.notifiedAmbulance.map { case (notifiedAmbulance, v, u) => (notifiedAmbulance.map(if (_) "Added Ambulance called" else "Removed Ambulance called").getOrElse("Not linked to incident"), v, u) }),
    "notifiedFire" -> toJson(r.notifiedFire.map { case (notifiedFire, v, u) => (notifiedFire.map(if (_) "Added Fire service called" else "Removed Fire service called").getOrElse("Not linked to incident"), v, u) }),
    "originalEnquiry" -> toJson(r.originalEnquiry),
    "caseType" -> toJson(r.caseType.map { case (caseType, v, u) => (caseType.map(_.description), v, u) }),
    "cause" -> toJson(r.cause.map { case (cause, v, u) => (cause.description, v, u) }),
    "tags" -> toJson(r.tags.map { case (tags, v, u) => (tags.map(_.description).toSeq.sorted.mkString(", "), v, u) }),
    "owners" -> toJson(r.owners.map { case (owners, v, u) => (owners.map(o => o.map(user => user.name.full.getOrElse(user.usercode.string)).fold(_.string, n => n)).toSeq.sorted.mkString(", "), v, u) }),
    "clients" -> toJson(r.clients.map { case (clients, v, u) => (clients.map(c => c.map(_.fullName).fold(_.string, n => n)).toSeq.sorted.mkString(", "), v, u) })
  )

  def apply(
    history: Seq[CaseVersion],
    rawTagHistory: Seq[StoredCaseTagVersion],
    rawOwnerHistory: Seq[OwnerVersion],
    rawClientHistory: Seq[CaseClientVersion],
    userLookupService: UserLookupService,
    profileService: ProfileService
  )(implicit t: TimingContext, ec: ExecutionContext): Future[ServiceResult[CaseHistory]] = {
    val usercodes = Seq(history, rawTagHistory, rawOwnerHistory, rawClientHistory).flatten.flatMap(_.auditUser) ++ rawOwnerHistory.map(_.userId)
    val usersByUsercode = userLookupService.getUsers(usercodes.distinct).toOption.getOrElse(Map())
    def toUsercodeOrUser(u: Usercode): Either[Usercode, User] = usersByUsercode.get(u).map(Right.apply).getOrElse(Left(u))

    profileService.getProfiles(rawClientHistory.map(_.client).toSet).map(_.map(profiles =>
      CaseHistory(
        subject = flatten(history.map(c => (c.subject, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        team = flatten(history.map(c => (c.team, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        state = flatten(history.map(c => (c.state, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        incidentDate = flatten(history.map(c => (c.incidentDate, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        onCampus = flatten(history.map(c => (c.onCampus, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        notifiedPolice = flatten(history.map(c => (c.notifiedPolice, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        notifiedAmbulance = flatten(history.map(c => (c.notifiedAmbulance, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        notifiedFire = flatten(history.map(c => (c.notifiedFire, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        originalEnquiry = flatten(history.map(c => (c.originalEnquiry, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        caseType = flatten(history.map(c => (c.caseType, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        cause = flatten(history.map(c => (c.cause, c.version, c.auditUser)).toList)
          .map { case (c, v, u) => (c, v, u.map(toUsercodeOrUser)) },
        tags = flattenCollection[StoredCaseTag, StoredCaseTagVersion](rawTagHistory.toList)
          .map { case (tags, v, u) => (tags.map(_.caseTag), v, u.map(toUsercodeOrUser))},
        owners = flattenCollection[Owner, OwnerVersion](rawOwnerHistory.toList)
          .map { case (owners, v, u) => (owners.map(o => usersByUsercode.get(o.userId).map(Right.apply).getOrElse(Left(o.userId))), v, u.map(toUsercodeOrUser))},
        clients = flattenCollection[CaseClient, CaseClientVersion](rawClientHistory.toList)
          .map { case (clients, v, u) => (clients.map(c => profiles.get(c.client).map(Right.apply).getOrElse(Left(c.client))), v, u.map(toUsercodeOrUser))},
      )
    ))
  }

  private def flatten[A](items: List[(A, OffsetDateTime, Option[Usercode])]): Seq[(A, OffsetDateTime, Option[Usercode])] = (items match {
    case Nil => Nil
    case head :: Nil => Seq(head)
    case head :: tail => tail.foldLeft(Seq(head)) { (foldedItems, item) =>
      if (foldedItems.last._1 != item._1) {
        foldedItems :+ item
      } else {
        foldedItems
      }
    }
  }).reverse

  private def flattenCollection[A <: Versioned[A], B <: StoredVersion[A]](items: List[B]): Seq[(Set[A], OffsetDateTime, Option[Usercode])] = {
    def toSpecificItem(item: B): A = item match {
      case tag: StoredCaseTagVersion => StoredCaseTag(tag.caseId, tag.caseTag, tag.version).asInstanceOf[A]
      case owner: OwnerVersion => Owner(owner.entityId, owner.entityType, owner.userId, owner.version).asInstanceOf[A]
      case client: CaseClientVersion => CaseClient(client.caseId, client.client, client.version).asInstanceOf[A]
      case _ => throw new IllegalArgumentException("Unsupported versioned item")
    }
    val result = items.sortBy(_.timestamp) match {
      case Nil => Nil
      case head :: Nil => List((Set(toSpecificItem(head)), head.version, head.auditUser))
      case head :: tail => tail.foldLeft[Seq[(Set[A], OffsetDateTime, Option[Usercode])]](Seq((Set(toSpecificItem(head)), head.version, head.auditUser))) { (result, item) =>
        if (item.operation == DatabaseOperation.Insert) {
          result.:+((result.last._1 + toSpecificItem(item), item.timestamp, item.auditUser))
        } else {
          result.:+((result.last._1 - toSpecificItem(item), item.timestamp, item.auditUser))
        }
      }
    }
    result
      // Group by identical timestamp and take the last one so bulk operations show as a single action
      .groupBy { case (_, t, _) => t }.mapValues(_.last).values.toSeq
      .sortBy { case (_, t, _) => t }
      .reverse
  }

  private def toJson[A](items: Seq[(A, OffsetDateTime, Option[Either[Usercode, User]])])(implicit itemWriter: Writes[A]): JsValue =
    Json.toJson(items.map { case (item, version, auditUser) => Json.obj(
      "value" -> Json.toJson(item),
      "version" -> version,
      "user" -> auditUser.map(_.fold(
        usercode => usercode.string,
        user => user.name.full.getOrElse(user.usercode.string)
      ))
    )})

}

case class CaseHistory(
  subject: Seq[(String, OffsetDateTime, Option[Either[Usercode, User]])],
  team: Seq[(Team, OffsetDateTime, Option[Either[Usercode, User]])],
  state: Seq[(IssueState, OffsetDateTime, Option[Either[Usercode, User]])],
  incidentDate: Seq[(Option[OffsetDateTime], OffsetDateTime, Option[Either[Usercode, User]])],
  onCampus: Seq[(Option[Boolean], OffsetDateTime, Option[Either[Usercode, User]])],
  notifiedPolice: Seq[(Option[Boolean], OffsetDateTime, Option[Either[Usercode, User]])],
  notifiedAmbulance: Seq[(Option[Boolean], OffsetDateTime, Option[Either[Usercode, User]])],
  notifiedFire: Seq[(Option[Boolean], OffsetDateTime, Option[Either[Usercode, User]])],
  originalEnquiry: Seq[(Option[UUID], OffsetDateTime, Option[Either[Usercode, User]])],
  caseType: Seq[(Option[CaseType], OffsetDateTime, Option[Either[Usercode, User]])],
  cause: Seq[(CaseCause, OffsetDateTime, Option[Either[Usercode, User]])],
  tags: Seq[(Set[CaseTag], OffsetDateTime, Option[Either[Usercode, User]])],
  owners: Seq[(Set[Either[Usercode, User]], OffsetDateTime, Option[Either[Usercode, User]])],
  clients: Seq[(Set[Either[UniversityID, SitsProfile]], OffsetDateTime, Option[Either[Usercode, User]])],
)