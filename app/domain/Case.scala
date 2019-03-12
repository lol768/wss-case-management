package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.DatabaseOperation.Delete
import domain.History._
import domain.dao.CaseDao._
import domain.dao.DSADao.{StoredDSAApplicationVersion, StoredDSAFundingType, StoredDSAFundingTypeVersion}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import play.api.libs.json.{Json, Writes}
import services.{AuditLogContext, CaseService, ClientService}
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, User, UserLookupService, Usercode}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

case class Case(
  id: UUID,
  key: IssueKey,
  subject: String,
  team: Team,
  state: IssueState,
  incident: Option[CaseIncident],
  originalEnquiry: Option[UUID],
  caseType: Option[CaseType],
  cause: CaseCause,
  dsaApplication: Option[UUID],
  fields: CaseFields,
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) extends Issue {
  def migrated = key.keyType == IssueKeyType.MigratedCase
}

case class CaseIncident(
  incidentDate: OffsetDateTime,
  onCampus: Boolean,
  notifiedPolice: Boolean,
  notifiedAmbulance: Boolean,
  notifiedFire: Boolean,
)

case class CaseFields(
  clientRiskTypes: Set[ClientRiskType],
  counsellingServicesIssues: Set[CounsellingServicesIssue],
  studentSupportIssueTypes: Set[StudentSupportIssueType],
  medications: Set[CaseMedication],
  severityOfProblem: Option[SeverityOfProblem],
  duty: Boolean,
)

object Case {
  def tupled = (apply _).tupled

  val SubjectMaxLength = 200

  // oldest first
  val dateOrdering: Ordering[Case] = Ordering.by[Case, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

/**
  * Just enough information to save or update a Case
  */
case class CaseSave(
  subject: String,
  incident: Option[CaseIncident],
  caseType: Option[CaseType],
  cause: CaseCause,
  clientRiskTypes: Set[ClientRiskType],
  counsellingServicesIssues: Set[CounsellingServicesIssue],
  studentSupportIssueTypes: Set[StudentSupportIssueType],
  medications: Set[CaseMedication],
  severityOfProblem: Option[SeverityOfProblem],
  duty: Boolean,
)

object CaseSave {
  def apply(c: Case): CaseSave =
    CaseSave(
      c.subject,
      c.incident,
      c.caseType,
      c.cause,
      c.fields.clientRiskTypes,
      c.fields.counsellingServicesIssues,
      c.fields.studentSupportIssueTypes,
      c.fields.medications,
      c.fields.severityOfProblem,
      c.fields.duty,
    )
}

case class CaseRender(
  clientCase: Case,
  messages: Seq[MessageRender],
  notes: Seq[CaseNote],
) {
  def migrated = clientCase.migrated

  def toIssue = IssueRender(
    clientCase,
    messages,
    CaseService.lastModified(this)
  )
}

case class CaseListRender(
  clientCase: Case,
  lastUpdated: OffsetDateTime,
  lastClientMessage: Option[OffsetDateTime],
  lastViewed: Option[OffsetDateTime],
) extends IssueListRender(clientCase)

case class NoteAndCase(
  note: CaseNote,
  clientCase: Case
)

case class CaseMessages(data: Seq[MessageRender]) {
  lazy val byClient: Map[UniversityID, Seq[MessageRender]] = data.groupBy(_.message.client).withDefaultValue(Nil)
  lazy val length: Int = data.length
}

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

case class CaseNoteRender(
  note: CaseNote,
  appointment: Option[AppointmentRender],
)

object CaseNote {
  // oldest first
  val dateOrdering: Ordering[CaseNote] = Ordering.by[CaseNote, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

object CaseNoteRender {
  // delegates to CaseNote.dateOrdering
  val dateOrdering: Ordering[CaseNoteRender] = Ordering.by[CaseNoteRender, CaseNote](_.note)(CaseNote.dateOrdering)
}

/**
  * Just the data of a case note required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class CaseNoteSave(
  text: String,
  teamMember: Usercode,
  appointmentID: Option[UUID],
)

sealed abstract class CaseNoteType(val description: String) extends EnumEntry
object CaseNoteType extends PlayEnum[CaseNoteType] {
  case object AppointmentNote extends CaseNoteType("Appointment note")
  case object AssociatedCase extends CaseNoteType("Associated case")
  case object DocumentNote extends CaseNoteType("Document added")
  case object CaseClosed extends CaseNoteType("Case closed")
  case object CaseReopened extends CaseNoteType("Case reopened")
  case object GeneralNote extends CaseNoteType("General note")
  case object OwnerNote extends CaseNoteType("Owner added")
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
  case object EmailCorrespondence extends CaseDocumentType("Email correspondence")
  case object Referral extends CaseDocumentType("Referral")
  case object Other extends CaseDocumentType("Other")

  override def values: immutable.IndexedSeq[CaseDocumentType] = findValues
}

object CaseHistory {

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
      "dsaCustomerReference" -> toJson(dsaHistory[Option[String]](r.dsaCustomerReference, ref => ref.getOrElse("Reference removed"))),
      "dsaApplicationDate" -> toJson(dsaHistory[Option[OffsetDateTime]](r.dsaApplicationDate, date => date.map(JavaTime.Relative.apply(_)).getOrElse("Application date removed"))),
      "dsaFundingApproved" -> toJson(dsaHistory[Option[Boolean]](r.dsaFundingApproved, approved => approved.map(if (_) "Application approved" else "Application rejected").getOrElse("Application pending"))),
      "dsaFundingTypes" -> toJson(r.dsaFundingTypes.map { case (tags, v, u) => (tags.map(_.description).toSeq.sorted.mkString(", "), v, u) }),
      "dsaConfirmationDate" -> toJson(dsaHistory[Option[OffsetDateTime]](r.dsaConfirmationDate, date => date.map(JavaTime.Relative.apply(_)).getOrElse("Decision date removed"))),
      "dsaIneligibilityReason" -> toJson(dsaHistory[Option[DSAIneligibilityReason]](r.dsaIneligibilityReason, reason => reason.map(_.description).getOrElse("Ineligibility reason removed"))),
      "clientRiskTypes" -> toJson(r.clientRiskTypes.map { case (clientRiskType, v, u) => (clientRiskType.map(_.description), v, u) }),
      "counsellingServicesIssues" -> toJson(r.counsellingServicesIssues.map { case (counsellingServicesIssue, v, u) => (counsellingServicesIssue.map(_.description), v, u) }),
      "studentSupportIssueTypes" -> toJson(r.studentSupportIssueTypes.map { case (studentSupportIssueType, v, u) => (studentSupportIssueType.map(_.description), v, u) }),
      "medications" -> toJson(r.medications.map { case (medication, v, u) => (medication.map(_.description), v, u) }),
      "severityOfProblem" -> toJson(r.severityOfProblem.map { case (severityOfProblem, v, u) => (severityOfProblem.map(_.description), v, u) })
    )

  def apply(
    history: Seq[StoredCaseVersion],
    rawTagHistory: Seq[StoredCaseTagVersion],
    rawOwnerHistory: Seq[OwnerVersion],
    rawClientHistory: Seq[StoredCaseClientVersion],
    rawDSAHistory: Seq[StoredDSAApplicationVersion],
    rawDSAFundingTypeHistory: Seq[StoredDSAFundingTypeVersion],
    userLookupService: UserLookupService,
    clientService: ClientService
  )(implicit ac: AuditLogContext, ec: ExecutionContext): Future[ServiceResult[CaseHistory]] = {
    val usercodes = Seq(history, rawTagHistory, rawOwnerHistory, rawClientHistory).flatten.flatMap(_.auditUser) ++ rawOwnerHistory.map(_.userId)
    implicit val usersByUsercode: Map[Usercode, User] = userLookupService.getUsers(usercodes.distinct).toOption.getOrElse(Map())

    def dsaFieldHistory[A](getValue: StoredDSAApplicationVersion => A): FieldHistory[Option[A]] = {
      val history = rawDSAHistory.map(dsa => {
        val value = if(dsa.operation == Delete) None else Some(getValue(dsa))
        (value, dsa.version, dsa.auditUser)
      })
      flatten(history).map { case (c,v,u) => (c, v, u.map(toUsercodeOrUser))}
    }

    def typedSimpleFieldHistory[A](f: StoredCaseVersion => A) = simpleFieldHistory[StoredCase, StoredCaseVersion, A](history, f)

    clientService.getOrAddClients(rawClientHistory.map(_.universityID).toSet).map(_.map(clients =>
      CaseHistory(
        subject = typedSimpleFieldHistory(_.subject),
        team = typedSimpleFieldHistory(_.team),
        state = typedSimpleFieldHistory(_.state),
        incidentDate = typedSimpleFieldHistory(_.incidentDate),
        onCampus = typedSimpleFieldHistory(_.onCampus),
        notifiedPolice = typedSimpleFieldHistory(_.notifiedPolice),
        notifiedAmbulance = typedSimpleFieldHistory(_.notifiedAmbulance),
        notifiedFire = typedSimpleFieldHistory(_.notifiedFire),
        originalEnquiry = typedSimpleFieldHistory(_.originalEnquiry),
        caseType = typedSimpleFieldHistory(_.caseType),
        cause = typedSimpleFieldHistory(_.cause),
        tags = flattenCollection[StoredCaseTag, StoredCaseTagVersion](rawTagHistory)
          .map { case (tags, v, u) => (tags.map(_.caseTag), v, u.map(toUsercodeOrUser))},
        owners = flattenCollection[Owner, OwnerVersion](rawOwnerHistory)
          .map { case (owners, v, u) => (owners.map(o => usersByUsercode.get(o.userId).map(Right.apply).getOrElse(Left(o.userId))), v, u.map(toUsercodeOrUser))},
        clients = flattenCollection[StoredCaseClient, StoredCaseClientVersion](rawClientHistory)
          .map { case (c, v, u) => (c.map(c => clients.find(_.universityID == c.universityID).get), v, u.map(toUsercodeOrUser))},
        dsaCustomerReference = dsaFieldHistory(_.customerReference),
        dsaApplicationDate = dsaFieldHistory(_.applicationDate),
        dsaFundingApproved = dsaFieldHistory(_.fundingApproved),
        dsaConfirmationDate = dsaFieldHistory(_.confirmationDate),
        dsaFundingTypes = flattenCollection[StoredDSAFundingType, StoredDSAFundingTypeVersion](rawDSAFundingTypeHistory)
          .map { case (fundingTypes, v, u) => (fundingTypes.map(_.fundingType), v, u.map(toUsercodeOrUser))},
        dsaIneligibilityReason = dsaFieldHistory(_.ineligibilityReason),
        clientRiskTypes = typedSimpleFieldHistory(_.fields.clientRiskTypes.map(ClientRiskType.withName).toSet),
        counsellingServicesIssues = typedSimpleFieldHistory(_.fields.counsellingServicesIssues.map(CounsellingServicesIssue.withName).toSet),
        studentSupportIssueTypes = typedSimpleFieldHistory(c => StudentSupportIssueType.apply(c.fields.studentSupportIssueTypes, c.fields.studentSupportIssueTypeOther)),
        medications = typedSimpleFieldHistory(c => CaseMedication.apply(c.fields.medications, c.fields.medicationOther)),
        severityOfProblem = typedSimpleFieldHistory(_.fields.severityOfProblem),
      )
    ))
  }

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
  dsaCustomerReference: FieldHistory[Option[Option[String]]],
  dsaApplicationDate: FieldHistory[Option[Option[OffsetDateTime]]],
  dsaFundingApproved: FieldHistory[Option[Option[Boolean]]],
  dsaConfirmationDate: FieldHistory[Option[Option[OffsetDateTime]]],
  dsaFundingTypes: FieldHistory[Set[DSAFundingType]],
  dsaIneligibilityReason: FieldHistory[Option[Option[DSAIneligibilityReason]]],
  clientRiskTypes: FieldHistory[Set[ClientRiskType]],
  counsellingServicesIssues: FieldHistory[Set[CounsellingServicesIssue]],
  studentSupportIssueTypes: FieldHistory[Set[StudentSupportIssueType]],
  medications: FieldHistory[Set[CaseMedication]],
  severityOfProblem: FieldHistory[Option[SeverityOfProblem]]
)