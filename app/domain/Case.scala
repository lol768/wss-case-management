package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CaseHistory.FieldHistory
import domain.DatabaseOperation.Delete
import domain.dao.CaseDao._
import domain.dao.DSADao.{DSAApplication, DSAApplicationVersion, StoredDSAFundingType, StoredDSAFundingTypeVersion}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import play.api.libs.json.{JsValue, Json, Writes}
import services.{AuditLogContext, ClientService}
import warwick.core.helpers.JavaTime
import warwick.sso.{User, UserLookupService, Usercode}

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
object CaseType extends PlayEnum[CaseType] {

  // Not currently any, but you never know if some may come back

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

case class DSAApplicationAndTypes(
  application: DSAApplication,
  fundingTypes: Set[DSAFundingType]
)

sealed abstract class DSAFundingType(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = entryName
}

object DSAFundingType extends PlayEnum[DSAFundingType] {
  case object AssistiveTechnology extends DSAFundingType("Assistive technology")
  case object NmhBand12 extends DSAFundingType("NMH Band 1 & 2")
  case object NmhBand34 extends DSAFundingType("NMH Band 3 & 4")
  case object GeneralAllowance extends DSAFundingType("General allowance")
  case object TravelCosts extends DSAFundingType("Taxi/travel costs")

  override def values: immutable.IndexedSeq[DSAFundingType] = findValues
}

sealed abstract class DSAIneligibilityReason(val description: String) extends EnumEntry
object DSAIneligibilityReason extends PlayEnum[DSAIneligibilityReason] {
  case object EUStudent extends DSAIneligibilityReason("EU student")
  case object InternationalStudent extends DSAIneligibilityReason("International student")
  case object HomeStudent extends DSAIneligibilityReason("Home student")
  case object NoApplication extends DSAIneligibilityReason("Decided not to apply")
  case object InsufficientEvidence extends DSAIneligibilityReason("Insufficient evidence")

  override def values: immutable.IndexedSeq[DSAIneligibilityReason] = findValues
}

case class CaseLink(
  id: UUID,
  linkType: CaseLinkType,
  outgoing: Case,
  incoming: Case,
  caseNote: CaseNote,
  teamMember: Member,
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
  teamMember: Member,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  lastUpdated: OffsetDateTime = JavaTime.offsetDateTime,
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
  teamMember: Member,
  caseNote: CaseNote,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  lastUpdated: OffsetDateTime = JavaTime.offsetDateTime,
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
  case object DSAEntitlementLetter extends CaseDocumentType("DSA Entitlement Letter")
  case object KeyLog extends CaseDocumentType("Key Log")
  case object MedicalEvidenceDocuments extends CaseDocumentType("Medical Evidence Documents")
  case object MentalHealthServicesOther extends CaseDocumentType("Mental Health Services - Other Documents")
  case object MitigatingCircumstancesForm extends CaseDocumentType("Mitigating Circumstances Form")
  case object Photos extends CaseDocumentType("Photos")
  case object PoliceIncidentDocument extends CaseDocumentType("Police Incident Document")
  case object ReleaseOfInformationConsentForm extends CaseDocumentType("Release Of Information Consent Form")
  case object SecurityReport extends CaseDocumentType("Security Report")
  case object SpecificLearningDifficultyDocument extends CaseDocumentType("Specific Learning Difficulty Document")
  case object WellbeingSupportInformationForm extends CaseDocumentType("Wellbeing Support Information Form")
  case object UIRForm extends CaseDocumentType("UIR Form")
  case object Other extends CaseDocumentType("Other")

  override def values: immutable.IndexedSeq[CaseDocumentType] = findValues
}

object CaseHistory {

  private type FieldHistory[A] = Seq[(A, OffsetDateTime, Option[Either[Usercode, User]])]

  private def optionalHistory[A](emptyMessage: String, history: FieldHistory[Option[A]], toHistoryDescription: A => String) =
    history.map{ case (value, v, u) => (value.map(toHistoryDescription).getOrElse(emptyMessage), v, u) }

  private def incidentHistory[A](history: FieldHistory[Option[A]], toHistoryDescription: A => String) =
    optionalHistory("Not linked to incident", history, toHistoryDescription)

  private def dsaHistory[A](history: FieldHistory[Option[A]], toHistoryDescription: A => String) =
    optionalHistory("Not a DSA application", history, toHistoryDescription)


  val writer: Writes[CaseHistory] = (r: CaseHistory) =>
    Json.obj(
      "subject" -> toJson(r.subject),
      "team" -> toJson(r.team)(Teams.writer),
      "state" -> toJson(r.state),
      "incidentDate" -> toJson(incidentHistory[OffsetDateTime](r.incidentDate, JavaTime.Relative.apply(_))),
      "onCampus" -> toJson(incidentHistory[Boolean](r.onCampus, if (_) "On-campus" else "Off-campus")),
      "notifiedPolice" -> toJson(incidentHistory[Boolean](r.notifiedPolice, if (_) "Added Police notified" else "Removed Police notified")),
      "notifiedAmbulance" -> toJson(incidentHistory[Boolean](r.notifiedAmbulance, if (_) "Added Ambulance called" else "Removed Ambulance called")),
      "notifiedFire" -> toJson(incidentHistory[Boolean](r.notifiedFire, if (_) "Added Fire service called" else "Removed Fire service called")),
      "originalEnquiry" -> toJson(r.originalEnquiry),
      "caseType" -> toJson(r.caseType.map { case (caseType, v, u) => (caseType.map(_.description), v, u) }),
      "cause" -> toJson(r.cause.map { case (cause, v, u) => (cause.description, v, u) }),
      "tags" -> toJson(r.tags.map { case (tags, v, u) => (tags.map(_.description).toSeq.sorted.mkString(", "), v, u) }),
      "owners" -> toJson(r.owners.map { case (owners, v, u) => (owners.map(o => o.map(user => user.name.full.getOrElse(user.usercode.string)).fold(_.string, n => n)).toSeq.sorted.mkString(", "), v, u) }),
      "clients" -> toJson(r.clients.map { case (clients, v, u) => (clients.map(c => c.safeFullName).toSeq.sorted.mkString(", "), v, u) }),
      "dsaApplicationDate" -> toJson(dsaHistory[Option[OffsetDateTime]](r.dsaApplicationDate, date => date.map(JavaTime.Relative.apply(_)).getOrElse("Application date removed"))),
      "dsaFundingApproved" -> toJson(dsaHistory[Option[Boolean]](r.dsaFundingApproved, approved => approved.map(if (_) "Application approved" else "Application rejected").getOrElse("Application pending"))),
      "dsaFundingTypes" -> toJson(r.dsaFundingTypes.map { case (tags, v, u) => (tags.map(_.description).toSeq.sorted.mkString(", "), v, u) }),
      "dsaConfirmationDate" -> toJson(dsaHistory[Option[OffsetDateTime]](r.dsaConfirmationDate, date => date.map(JavaTime.Relative.apply(_)).getOrElse("Decision date removed"))),
      "dsaIneligibilityReason" -> toJson(dsaHistory[Option[DSAIneligibilityReason]](r.dsaIneligibilityReason, reason => reason.map(_.description).getOrElse("Ineligibility reason removed"))),
    )

  def apply(
    history: Seq[CaseVersion],
    rawTagHistory: Seq[StoredCaseTagVersion],
    rawOwnerHistory: Seq[OwnerVersion],
    rawClientHistory: Seq[StoredCaseClientVersion],
    rawDSAHistory: Seq[DSAApplicationVersion],
    rawDSAFundingTypeHistory: Seq[StoredDSAFundingTypeVersion],
    userLookupService: UserLookupService,
    clientService: ClientService
  )(implicit ac: AuditLogContext, ec: ExecutionContext): Future[ServiceResult[CaseHistory]] = {
    val usercodes = Seq(history, rawTagHistory, rawOwnerHistory, rawClientHistory).flatten.flatMap(_.auditUser) ++ rawOwnerHistory.map(_.userId)
    val usersByUsercode = userLookupService.getUsers(usercodes.distinct).toOption.getOrElse(Map())
    def toUsercodeOrUser(u: Usercode): Either[Usercode, User] = usersByUsercode.get(u).map(Right.apply).getOrElse(Left(u))

    def simpleFieldHistory[A](getValue: CaseVersion => A): FieldHistory[A] =
      flatten(history.map(c => (getValue(c), c.version, c.auditUser))).map {
        case (c,v,u) => (c, v, u.map(toUsercodeOrUser))
      }

    def dsaFieldHistory[A](getValue: DSAApplicationVersion => A): FieldHistory[Option[A]] = {
      val history = rawDSAHistory.map(dsa => {
        val value = if(dsa.operation == Delete) None else Some(getValue(dsa))
        (value, dsa.version, dsa.auditUser)
      })
      flatten(history).map { case (c,v,u) => (c, v, u.map(toUsercodeOrUser))}
    }

    clientService.getOrAddClients(rawClientHistory.map(_.universityID).toSet).map(_.map(clients =>
      CaseHistory(
        subject = simpleFieldHistory(_.subject),
        team = simpleFieldHistory(_.team),
        state = simpleFieldHistory(_.state),
        incidentDate = simpleFieldHistory(_.incidentDate),
        onCampus = simpleFieldHistory(_.onCampus),
        notifiedPolice = simpleFieldHistory(_.notifiedPolice),
        notifiedAmbulance = simpleFieldHistory(_.notifiedAmbulance),
        notifiedFire = simpleFieldHistory(_.notifiedFire),
        originalEnquiry = simpleFieldHistory(_.originalEnquiry),
        caseType = simpleFieldHistory(_.caseType),
        cause = simpleFieldHistory(_.cause),
        tags = flattenCollection[StoredCaseTag, StoredCaseTagVersion](rawTagHistory)
          .map { case (tags, v, u) => (tags.map(_.caseTag), v, u.map(toUsercodeOrUser))},
        owners = flattenCollection[Owner, OwnerVersion](rawOwnerHistory)
          .map { case (owners, v, u) => (owners.map(o => usersByUsercode.get(o.userId).map(Right.apply).getOrElse(Left(o.userId))), v, u.map(toUsercodeOrUser))},
        clients = flattenCollection[StoredCaseClient, StoredCaseClientVersion](rawClientHistory)
          .map { case (c, v, u) => (c.map(c => clients.find(_.universityID == c.universityID).get), v, u.map(toUsercodeOrUser))},
        dsaApplicationDate = dsaFieldHistory(_.applicationDate),
        dsaFundingApproved = dsaFieldHistory(_.fundingApproved),
        dsaConfirmationDate = dsaFieldHistory(_.confirmationDate),
        dsaFundingTypes = flattenCollection[StoredDSAFundingType, StoredDSAFundingTypeVersion](rawDSAFundingTypeHistory)
          .map { case (fundingTypes, v, u) => (fundingTypes.map(_.fundingType), v, u.map(toUsercodeOrUser))},
        dsaIneligibilityReason = dsaFieldHistory(_.ineligibilityReason),
      )
    ))
  }

  private def flatten[A](items: Seq[(A, OffsetDateTime, Option[Usercode])]): Seq[(A, OffsetDateTime, Option[Usercode])] = (items.toList match {
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

  private def flattenCollection[A <: Versioned[A], B <: StoredVersion[A]](items: Seq[B]): Seq[(Set[A], OffsetDateTime, Option[Usercode])] = {
    def toSpecificItem(item: B): A = item match {
      case tag: StoredCaseTagVersion => StoredCaseTag(tag.caseId, tag.caseTag, tag.version).asInstanceOf[A]
      case owner: OwnerVersion => Owner(owner.entityId, owner.entityType, owner.userId, owner.outlookId, owner.version).asInstanceOf[A]
      case client: StoredCaseClientVersion => StoredCaseClient(client.caseId, client.universityID, client.version).asInstanceOf[A]
      case ft: StoredDSAFundingTypeVersion => StoredDSAFundingType(ft.dsaApplicationID, ft.fundingType, ft.version).asInstanceOf[A]
      case _ => throw new IllegalArgumentException("Unsupported versioned item")
    }
    val result = items.toList.sortBy(_.timestamp) match {
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

  private def toJson[A](items: FieldHistory[A])(implicit itemWriter: Writes[A]): JsValue =
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
  subject: FieldHistory[String],
  team: FieldHistory[Team],
  state: FieldHistory[IssueState],
  incidentDate: FieldHistory[Option[OffsetDateTime]],
  onCampus: FieldHistory[Option[Boolean]],
  notifiedPolice: FieldHistory[Option[Boolean]],
  notifiedAmbulance: FieldHistory[Option[Boolean]],
  notifiedFire: FieldHistory[Option[Boolean]],
  originalEnquiry: FieldHistory[Option[UUID]],
  caseType: FieldHistory[Option[CaseType]],
  cause: FieldHistory[CaseCause],
  tags: FieldHistory[Set[CaseTag]],
  owners: Seq[(Set[Either[Usercode, User]], OffsetDateTime, Option[Either[Usercode, User]])],
  clients: FieldHistory[Set[Client]],
  dsaApplicationDate: FieldHistory[Option[Option[OffsetDateTime]]],
  dsaFundingApproved: FieldHistory[Option[Option[Boolean]]],
  dsaConfirmationDate: FieldHistory[Option[Option[OffsetDateTime]]],
  dsaFundingTypes: FieldHistory[Set[DSAFundingType]],
  dsaIneligibilityReason: FieldHistory[Option[Option[DSAIneligibilityReason]]],
)