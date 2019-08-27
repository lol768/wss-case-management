package domain.dao

import java.time._
import java.util.UUID

import akka.Done
import com.github.tminglei.slickpg.TsVector
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.QueryHelpers._
import domain.dao.CaseDao._
import domain.dao.ClientDao.StoredClient
import domain.dao.ClientDao.StoredClient.Clients
import domain.dao.MemberDao.StoredMember.Members
import domain.{Case, _}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.lifted.ProvenShape
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[CaseDaoImpl])
trait CaseDao {
  def insert(c: StoredCase)(implicit ac: AuditLogContext): DBIO[StoredCase]
  def find(id: UUID): DBIO[StoredCase]
  def find(ids: Set[UUID]): DBIO[Seq[StoredCase]]
  def find(key: IssueKey): DBIO[StoredCase]
  def findAll(ids: Set[UUID]): DBIO[Seq[StoredCase]]
  def findByIDQuery(id: UUID): Query[Cases, StoredCase, Seq]
  def findByIDsQuery(ids: Set[UUID]): Query[Cases, StoredCase, Seq]
  def findByKeyQuery(key: IssueKey): Query[Cases, StoredCase, Seq]
  def findByClientQuery(universityID: UniversityID): Query[Cases, StoredCase, Seq]
  def searchQuery(query: CaseSearchQuery): Query[Cases, StoredCase, Seq]
  def update(c: StoredCase, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCase]
  def insertTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseTag]]
  def insertTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[StoredCaseTag]
  def deleteTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Done]
  def deleteTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[Done]
  def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq]
  def insertClients(clients: Set[StoredCaseClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseClient]]
  def insertClient(client: StoredCaseClient)(implicit ac: AuditLogContext): DBIO[StoredCaseClient]
  def deleteClients(client: Set[StoredCaseClient])(implicit ac: AuditLogContext): DBIO[Done]
  def deleteClient(client: StoredCaseClient)(implicit ac: AuditLogContext): DBIO[Done]
  def findClientsQuery(caseIds: Set[UUID]): Query[(CaseClients, StoredClient.Clients), (StoredCaseClient, ClientDao.StoredClient), Seq]
  def insertLink(link: StoredCaseLink)(implicit ac: AuditLogContext): DBIO[StoredCaseLink]
  def deleteLink(link: StoredCaseLink, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq]
  def insertNote(note: StoredCaseNote)(implicit ac: AuditLogContext): DBIO[StoredCaseNote]
  def findNote(id: UUID): DBIO[NoteAndCase]
  def updateNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCaseNote]
  def deleteNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq]
  def findNotesQuery(caseIDs: Set[UUID]): Query[CaseNotes, StoredCaseNote, Seq]
  def insertDocument(document: StoredCaseDocument)(implicit ac: AuditLogContext): DBIO[StoredCaseDocument]
  def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq]
  def listQuery(filter: CaseFilter): Query[Cases, StoredCase, Seq]
  def getHistory(id: UUID): DBIO[Seq[StoredCaseVersion]]
  def getTagHistory(caseID: UUID): DBIO[Seq[StoredCaseTagVersion]]
  def getClientHistory(caseID: UUID): DBIO[Seq[StoredCaseClientVersion]]
  def findByOriginalEnquiryQuery(enquiryId: UUID): Query[Cases, StoredCase, Seq]
  def getLastUpdatedForClients(clients: Set[UniversityID]): DBIO[Seq[(UniversityID, Option[OffsetDateTime])]]
  def findCasesWithEnquiriesQuery(state: IssueStateFilter): Query[Cases, StoredCase, Seq]
  def findCasesWithoutEnquiriesQuery(state: IssueStateFilter): Query[Cases, StoredCase, Seq]
}

@Singleton
class CaseDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CaseDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  override def insert(c: StoredCase)(implicit ac: AuditLogContext): DBIO[StoredCase] =
    cases.insert(c)

  override def find(id: UUID): DBIO[StoredCase] =
    findByIDQuery(id).result.head

  override def find(ids: Set[UUID]): DBIO[Seq[StoredCase]] =
    findByIDsQuery(ids).result

  override def find(key: IssueKey): DBIO[StoredCase] =
    findByKeyQuery(key).result.head

  override def findAll(ids: Set[UUID]): DBIO[Seq[StoredCase]] =
    findByIDsQuery(ids).result

  override def findByIDQuery(id: UUID): Query[Cases, StoredCase, Seq] =
    cases.table.filter(_.id === id)

  override def findByIDsQuery(ids: Set[UUID]): Query[Cases, StoredCase, Seq] =
    cases.table.filter(_.id.inSet(ids))

  override def findByKeyQuery(key: IssueKey): Query[Cases, StoredCase, Seq] =
    cases.table.filter(_.key === key)

  override def findByClientQuery(universityID: UniversityID): Query[Cases, StoredCase, Seq] =
    cases.table
      .withClients
      .filter { case (_, client, _) => client.universityID === universityID }
      .map { case (c, _, _) => c }

  override def searchQuery(q: CaseSearchQuery): Query[Cases, StoredCase, Seq] = {
    def queries(c: Cases, client: Clients, n: Rep[Option[CaseNotes]], tm: Rep[Option[Members]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          (
            c.searchableKey @+
            c.searchableSubject @+
            client.searchableUniversityID @+
            client.searchableFullName @+
            n.map(_.searchableText).orEmptyTsVector @+
            tm.map(_.searchableUsercode).orEmptyTsVector @+
            tm.map(_.searchableFullName).orEmptyTsVector
          ).? @@ prefixTsQuery(queryStr.bind)
        },
        q.createdAfter.map { d => c.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => c.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        (q.team, q.member) match {
          // CASE-465 This is intentionally an OR
          case (Some(team), Some(member)) => Some(c.team.? === team || tm.map(_.usercode === member))
          case (Some(team), _) => Some(c.team.? === team)
          case (_, Some(member)) => Some(tm.map(_.usercode === member))
          case _ => None
        },
        q.caseType.map { caseType => c.caseType === caseType },
        q.state.flatMap {
          case IssueStateFilter.All => None
          case IssueStateFilter.Open => Some(c.isOpen.?)
          case IssueStateFilter.Closed => Some(!c.isOpen.?)
        }
      ).flatten

    cases.table
      .withClients
      .joinLeft(caseNotes.table)
      .on { case ((c, _, _), n) => c.id === n.caseId }
      .joinLeft(Owner.owners.table.join(MemberDao.members.table).on(_.userId === _.usercode))
      .on { case (((c, _, _), _), (o, _)) => c.id === o.entityId && o.entityType === (Owner.EntityType.Case: Owner.EntityType) }
      .map { case (((c, _, client), n), o) => (c, client, n, o.map { case (_, tm) => tm }) }
      .filter { case (c, client, n, tm) => queries(c, client, n, tm).reduce(_ && _) }
      .map { case (c, _, _, _) => (c, c.isOpen) }
      .sortBy { case (c, isOpen) => (isOpen.desc, c.created.desc) }
      .distinct
      .map { case (c, _) => c }
  }

  override def update(c: StoredCase, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCase] =
    cases.update(c.copy(version = version))

  override def insertTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseTag]] =
    caseTags.insertAll(tags.toSeq)

  override def insertTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[StoredCaseTag] =
    caseTags.insert(tag)

  override def deleteTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Done] =
    caseTags.deleteAll(tags.toSeq)

  override def deleteTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[Done] =
    caseTags.delete(tag)

  override def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq] =
    caseTags.table
      .filter(_.caseId.inSet(caseIds))

  override def insertClients(clients: Set[StoredCaseClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseClient]] =
    caseClients.insertAll(clients.toSeq)

  override def insertClient(client: StoredCaseClient)(implicit ac: AuditLogContext): DBIO[StoredCaseClient] =
    caseClients.insert(client)

  override def deleteClients(clients: Set[StoredCaseClient])(implicit ac: AuditLogContext): DBIO[Done] =
    caseClients.deleteAll(clients.toSeq)

  override def deleteClient(client: StoredCaseClient)(implicit ac: AuditLogContext): DBIO[Done] =
    caseClients.delete(client)

  override def findClientsQuery(caseIds: Set[UUID]): Query[(CaseClients, StoredClient.Clients), (StoredCaseClient, ClientDao.StoredClient), Seq] =
    caseClients.table
      .filter(_.caseId.inSet(caseIds))
      .withClients

  override def insertLink(link: StoredCaseLink)(implicit ac: AuditLogContext): DBIO[StoredCaseLink] =
    caseLinks.insert(link)

  override def deleteLink(link: StoredCaseLink, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseLinks.delete(link.copy(version = version))

  override def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq] =
    caseLinks.table.filter { l => l.outgoingCaseID === caseID || l.incomingCaseID === caseID }

  override def findNote(id: UUID): DBIO[NoteAndCase] =
    caseNotes.table.filter(_.id === id)
      .withMember
      .join(cases.table)
      .on { case ((n, _), c) => n.caseId === c.id }
      .flattenJoin
      .result.head
      .map { case (n, m, c) => NoteAndCase(n.asCaseNote(m.asMember), c.asCase)}

  override def insertNote(note: StoredCaseNote)(implicit ac: AuditLogContext): DBIO[StoredCaseNote] =
    caseNotes.insert(note)

  override def updateNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCaseNote] =
    caseNotes.update(note.copy(version = version))

  override def deleteNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseNotes.delete(note.copy(version = version))

  override def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq] =
    caseNotes.table.filter(_.caseId === caseID)

  override def findNotesQuery(caseIDs: Set[UUID]): Query[CaseNotes, StoredCaseNote, Seq] =
    caseNotes.table.filter(_.caseId.inSet(caseIDs))

  override def insertDocument(document: StoredCaseDocument)(implicit ac: AuditLogContext): DBIO[StoredCaseDocument] =
    caseDocuments.insert(document)

  override def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseDocuments.delete(document.copy(version = version))

  override def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq] =
    caseDocuments.table.filter(_.caseId === caseID)

  override def listQuery(filter: CaseFilter): Query[Cases, StoredCase, Seq] = {
    Option(filter.owner).filter(_.nonEmpty).fold(cases.table.subquery)(usercodes =>
      cases.table
        .join(Owner.owners.table)
        .on((c, o) => c.id === o.entityId && o.entityType === (Owner.EntityType.Case : Owner.EntityType))
        .filter { case (_, o) => o.userId.inSet(usercodes) }
        .map { case (c, _) => c }
    ).filter(c => {
      val teamFilter = filter.team.fold(true.bind)(c.team === _)
      val stateFilter = filter.state match {
        case IssueStateFilter.Open => c.isOpen
        case IssueStateFilter.Closed => !c.isOpen
        case IssueStateFilter.All => true.bind
      }
      teamFilter && stateFilter
    })
  }

  override def getHistory(id: UUID): DBIO[Seq[StoredCaseVersion]] = cases.history(_.id === id)

  override def getTagHistory(caseID: UUID): DBIO[Seq[StoredCaseTagVersion]] = {
    caseTags.versionsTable.filter(t => t.caseId === caseID).result
  }

  override def getClientHistory(caseID: UUID): DBIO[Seq[StoredCaseClientVersion]] = {
    caseClients.versionsTable.filter(c => c.caseId === caseID).result
  }

  override def findByOriginalEnquiryQuery(enquiryId: UUID): Query[Cases, StoredCase, Seq] = {
    cases.table.filter(c => c.originalEnquiry.map(_ === enquiryId))
  }

  override def getLastUpdatedForClients(clients: Set[UniversityID]): DBIO[Seq[(UniversityID, Option[OffsetDateTime])]] = {
    cases.table
      .withClients.filter { case (_, c, _) => c.universityID.inSet(clients) }
      .joinLeft(Message.lastUpdatedCaseMessage)
      .on { case ((c, _, _), (id, _)) => c.id === id }
      .joinLeft(CaseDao.lastUpdatedCaseNote)
      .on { case (((c, _, _), _), (id, _)) => c.id === id }
      .map { case (((c, client, _), messages), notes) => (client.universityID, c.version, messages.flatMap(_._2), notes.flatMap(_._2)) }
      .groupBy { case (client, _, _, _) => client }
      .map { case (client, tuple) => (client, tuple.map(_._2).max, tuple.map(_._3).max, tuple.map(_._4).max) }
      .map { case (client, caseUpdated, m, n) =>
        // working out the most recent date is made easier if we deal with an arbitrary min date rather than handling the options
        val MinDate = OffsetDateTime.from(Instant.EPOCH.atOffset(ZoneOffset.UTC))

        val latestMessage = m.getOrElse(MinDate)
        val latestNote = n.getOrElse(MinDate)

        val mostRecentUpdate = slick.lifted.Case.If((caseUpdated > latestMessage) && (caseUpdated > latestNote)).Then(caseUpdated)
          .If((latestMessage > caseUpdated) && (latestMessage > latestNote)).Then(latestMessage.?)
          .Else(latestNote.?)

        (client, mostRecentUpdate)
      }
      .result
  }

  override def findCasesWithEnquiriesQuery(state: IssueStateFilter): Query[Cases, StoredCase, Seq] = {
    cases.table.filter(c => c.originalEnquiry.isDefined && c.matchesState(state))
  }

  override def findCasesWithoutEnquiriesQuery(state: IssueStateFilter): Query[Cases, StoredCase, Seq] = {
    cases.table.filter(c => c.originalEnquiry.isEmpty && c.matchesState(state))
  }
}

object CaseDao {

  val cases: VersionedTableQuery[StoredCase, StoredCaseVersion, Cases, CaseVersions] =
    VersionedTableQuery(TableQuery[Cases], TableQuery[CaseVersions])

  val caseTags: VersionedTableQuery[StoredCaseTag, StoredCaseTagVersion, CaseTags, CaseTagVersions] =
    VersionedTableQuery(TableQuery[CaseTags], TableQuery[CaseTagVersions])

  val caseClients: VersionedTableQuery[StoredCaseClient, StoredCaseClientVersion, CaseClients, CaseClientVersions] =
    VersionedTableQuery(TableQuery[CaseClients], TableQuery[CaseClientVersions])

  val caseLinks: VersionedTableQuery[StoredCaseLink, StoredCaseLinkVersion, CaseLinks, CaseLinkVersions] =
    VersionedTableQuery(TableQuery[CaseLinks], TableQuery[CaseLinkVersions])

  val caseNotes: VersionedTableQuery[StoredCaseNote, StoredCaseNoteVersion, CaseNotes, CaseNoteVersions] =
    VersionedTableQuery(TableQuery[CaseNotes], TableQuery[CaseNoteVersions])

  val caseDocuments: VersionedTableQuery[StoredCaseDocument, StoredCaseDocumentVersion, CaseDocuments, CaseDocumentVersions] =
    VersionedTableQuery(TableQuery[CaseDocuments], TableQuery[CaseDocumentVersions])

  case class StoredCase(
    id: UUID,
    key: IssueKey,
    subject: String,
    created: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    incidentDate: Option[OffsetDateTime],
    onCampus: Option[Boolean],
    notifiedPolice: Option[Boolean],
    notifiedAmbulance: Option[Boolean],
    notifiedFire: Option[Boolean],
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause,
    dsaApplication: Option[UUID],
    fields: StoredCaseFields,
  ) extends Versioned[StoredCase] with Created with Teamable {
    def asCase: Case =
      Case(
        id = id,
        key = key,
        subject = subject,
        team = team,
        state = state,
        incident = incidentDate.map { d =>
          CaseIncident(
            incidentDate = d,
            onCampus = onCampus.get,
            notifiedPolice = notifiedPolice.get,
            notifiedAmbulance = notifiedAmbulance.get,
            notifiedFire = notifiedFire.get,
          )
        },
        originalEnquiry = originalEnquiry,
        caseType = caseType,
        cause = cause,
        dsaApplication = dsaApplication,
        fields = fields.asCaseFields,
        created = created,
        lastUpdated = version,
      )

    override def atVersion(at: OffsetDateTime): StoredCase = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredCase]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseVersion(
        id,
        key,
        subject,
        created,
        team,
        version,
        state,
        incidentDate,
        onCampus,
        notifiedPolice,
        notifiedAmbulance,
        notifiedFire,
        originalEnquiry,
        caseType,
        cause,
        dsaApplication,
        fields,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  /**
    * Not a table directly; used to get around the tuple arity limit
    */
  case class StoredCaseFields(
    clientRiskTypes: List[String],
    counsellingServicesIssues: List[String],
    studentSupportIssueTypes: List[String],
    studentSupportIssueTypeOther: Option[String],
    mentalHealthIssues: List[String],
    medications: List[String],
    medicationOther: Option[String],
    severityOfProblem: Option[SeverityOfProblem],
    duty: Boolean,
  ) {
    def asCaseFields: CaseFields = CaseFields(
      clientRiskTypes = clientRiskTypes.toSet.map(ClientRiskType.withName),
      counsellingServicesIssues = counsellingServicesIssues.toSet.map(CounsellingServicesIssue.withName),
      studentSupportIssueTypes = StudentSupportIssueType(studentSupportIssueTypes, studentSupportIssueTypeOther),
      mentalHealthIssues = mentalHealthIssues.toSet.map(MentalHealthIssue.withName),
      medications = CaseMedication(medications, medicationOther),
      severityOfProblem = severityOfProblem,
      duty = duty,
    )
  }

  case class StoredCaseVersion(
    id: UUID,
    key: IssueKey,
    subject: String,
    created: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    incidentDate: Option[OffsetDateTime],
    onCampus: Option[Boolean],
    notifiedPolice: Option[Boolean],
    notifiedAmbulance: Option[Boolean],
    notifiedFire: Option[Boolean],
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause,
    dsaApplication: Option[UUID],
    fields: StoredCaseFields,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCase] with Created

  trait CommonProperties { self: Table[_] =>
    def key = column[IssueKey]("case_key")
    def subject = column[String]("subject")
    def created = column[OffsetDateTime]("created_utc")
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def state = column[IssueState]("state")
    def incidentDate = column[Option[OffsetDateTime]]("incident_date_utc")
    def onCampus = column[Option[Boolean]]("on_campus")
    def notifiedPolice = column[Option[Boolean]]("notified_police")
    def notifiedAmbulance = column[Option[Boolean]]("notified_ambulance")
    def notifiedFire = column[Option[Boolean]]("notified_fire")
    def originalEnquiry = column[Option[UUID]]("enquiry_id")
    def caseType = column[Option[CaseType]]("case_type")
    def cause = column[CaseCause]("cause")
    def dsaApplication = column[Option[UUID]]("dsa_application")
    def clientRiskTypes = column[List[String]]("client_risk_types")
    def counsellingServicesIssues = column[List[String]]("counselling_services_issues")
    def studentSupportIssueTypes = column[List[String]]("student_support_issue_types")
    def studentSupportIssueTypeOther = column[Option[String]]("student_support_issue_type_other")
    def mentalHealthIssues = column[List[String]]("mental_health_issues")
    def medications = column[List[String]]("medications")
    def medicationOther = column[Option[String]]("medication_other")
    def severityOfProblem = column[Option[SeverityOfProblem]]("severity_of_problem")
    def duty = column[Boolean]("duty")

    protected def fieldsProjection = (clientRiskTypes, counsellingServicesIssues, studentSupportIssueTypes, studentSupportIssueTypeOther, mentalHealthIssues, medications, medicationOther, severityOfProblem, duty).mapTo[StoredCaseFields]
  }

  class Cases(tag: Tag) extends Table[StoredCase](tag, "client_case")
    with VersionedTable[StoredCase]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredCase): Rep[Boolean] = id === other.id
    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
    def searchableKey: Rep[TsVector] = column[TsVector]("case_key_tsv")
    def searchableSubject: Rep[TsVector] = column[TsVector]("subject_tsv")

    def isOpen: Rep[Boolean] = state === (IssueState.Open : IssueState) || state === (IssueState.Reopened : IssueState)

    def matchesState(state: IssueStateFilter): Rep[Boolean] = state match {
      case IssueStateFilter.Open => isOpen
      case IssueStateFilter.Closed => !isOpen
      case IssueStateFilter.All => true.bind
    }

    override def * : ProvenShape[StoredCase] =
      (id, key, subject, created, team, version, state, incidentDate, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause, dsaApplication, fieldsProjection).mapTo[StoredCase]
    def idx = index("idx_client_case_key", key, unique = true)
  }

  class CaseVersions(tag: Tag) extends Table[StoredCaseVersion](tag, "client_case_version")
    with StoredVersionTable[StoredCase]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseVersion] =
      (id, key, subject, created, team, version, state, incidentDate, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause, dsaApplication, fieldsProjection, operation, timestamp, auditUser).mapTo[StoredCaseVersion]
  }

  implicit class CaseExtensions[C[_]](val q: Query[Cases, StoredCase, C]) extends AnyVal {
    def withClients: Query[(Cases, CaseClients, StoredClient.Clients), (StoredCase, StoredCaseClient, StoredClient), C] = q
      .join(caseClients.table)
      .on(_.id === _.caseId)
      .join(ClientDao.clients.table)
      .on { case ((_, cc), client) => cc.universityID === client.universityID }
      .flattenJoin

    def withNotes = q
      .joinLeft(caseNotes.table)
      .on(_.id === _.caseId)

    def withMessages = q
      .joinLeft(
        Message.messages.table
          .withUploadedFiles
          .joinLeft(MemberDao.members.table)
          .on { case ((m, _), member) => m.teamMember.map(_ === member.usercode) }
          .map { case ((m, f), member) => (m, f, member) }
      )
      .on { case (c, (m, _, _)) =>
        c.id === m.ownerId && m.ownerType === (MessageOwner.Case: MessageOwner)
      }

    def withLastUpdatedFor(usercode: Usercode) = q
      .joinLeft(Message.lastUpdatedCaseMessage)
      .on { case (c, (id, _)) => c.id === id }
      .map { case (c, o) => (c, o.flatMap(_._2)) }
      .joinLeft(CaseDao.lastUpdatedCaseNote)
      .on { case ((c, _), (id, _)) => c.id === id }
      .map { case ((c, messageCreated), o) => (c, messageCreated, o.flatMap(_._2)) }
      .joinLeft(Message.lastUpdatedCaseMessageFromClient)
      .on { case ((c, _, _), (id, _)) => c.id === id }
      .map { case ((c, m, n), o) => (c, m, n, o.flatMap(_._2)) }
      .joinLeft(AuditEvent.latestEventsForUser(Operation.Case.View, usercode, Target.Case))
      .on { case ((c, _, _, _), (targetId, _)) => c.id.asColumnOf[String] === targetId }
      .map { case ((c, m, n, o), p) => (c, m, n, o, p.flatMap(_._2)) }
      .map { case (c, m, n, lastMessageFromClient, lastViewed) =>
        // working out the most recent date is made easier if we deal with an arbitrary min date rather than handling the options
        val MinDate = OffsetDateTime.from(Instant.EPOCH.atOffset(ZoneOffset.UTC))

        val caseUpdated = c.version
        val latestMessage = m.getOrElse(MinDate)
        val latestNote = n.getOrElse(MinDate)

        val mostRecentUpdate = slick.lifted.Case.If((caseUpdated > latestMessage) && (caseUpdated > latestNote)).Then(caseUpdated)
          .If((latestMessage > caseUpdated) && (latestMessage > latestNote)).Then(latestMessage)
          .Else(latestNote)

        // Only consider the last message from client if it's the most recent message in general
        val latestMessageFromClient = lastMessageFromClient.getOrElse(MinDate)
        val mostRecentMessageFromClient: Rep[Option[OffsetDateTime]] =
          slick.lifted.Case.If(latestMessage > latestMessageFromClient)
            .Then(Option.empty[OffsetDateTime])
            .Else(lastMessageFromClient)

        (c, mostRecentUpdate, mostRecentMessageFromClient, lastViewed)
      }
  }

  case class StoredCaseTag(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredCaseTag] {
    override def atVersion(at: OffsetDateTime): StoredCaseTag = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredCaseTag]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseTagVersion(
        caseId,
        caseTag,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseTagVersion(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseTag]

  trait CommonTagProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def caseTag = column[CaseTag]("tag")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseTags(tag: Tag) extends Table[StoredCaseTag](tag, "client_case_tag")
    with VersionedTable[StoredCaseTag]
    with CommonTagProperties {
    override def matchesPrimaryKey(other: StoredCaseTag): Rep[Boolean] =
      caseId === other.caseId && caseTag === other.caseTag

    override def * : ProvenShape[StoredCaseTag] =
      (caseId, caseTag, version).mapTo[StoredCaseTag]
    def pk = primaryKey("pk_case_tag", (caseId, caseTag))
    def fk = foreignKey("fk_case_tag", caseId, cases.table)(_.id)
    def idx = index("idx_case_tag", caseId)
  }

  class CaseTagVersions(tag: Tag) extends Table[StoredCaseTagVersion](tag, "client_case_tag_version")
    with StoredVersionTable[StoredCaseTag]
    with CommonTagProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseTagVersion] =
      (caseId, caseTag, version, operation, timestamp, auditUser).mapTo[StoredCaseTagVersion]
    def pk = primaryKey("pk_case_tag_version", (caseId, caseTag, timestamp))
    def idx = index("idx_case_tag_version", (caseId, caseTag, version))
  }

  case class StoredCaseClient(
    caseId: UUID,
    universityID: UniversityID,
    version: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredCaseClient] {
    override def atVersion(at: OffsetDateTime): StoredCaseClient = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredCaseClient]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseClientVersion(
        caseId,
        universityID,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseClientVersion(
    caseId: UUID,
    universityID: UniversityID,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseClient]

  trait CommonClientProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def universityID = column[UniversityID]("university_id")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseClients(tag: Tag) extends Table[StoredCaseClient](tag, "client_case_client")
    with VersionedTable[StoredCaseClient]
    with CommonClientProperties {
    override def matchesPrimaryKey(other: StoredCaseClient): Rep[Boolean] =
      caseId === other.caseId && universityID === other.universityID

    override def * : ProvenShape[StoredCaseClient] =
      (caseId, universityID, version).mapTo[StoredCaseClient]
    def pk = primaryKey("pk_case_client", (caseId, universityID))
    def fk = foreignKey("fk_case_client", caseId, cases.table)(_.id)
    def caseIndex = index("idx_case_client", caseId)
    def clientIndex = index("idx_case_client_university_id", universityID)
  }

  class CaseClientVersions(tag: Tag) extends Table[StoredCaseClientVersion](tag, "client_case_client_version")
    with StoredVersionTable[StoredCaseClient]
    with CommonClientProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseClientVersion] =
      (caseId, universityID, version, operation, timestamp, auditUser).mapTo[StoredCaseClientVersion]
    def pk = primaryKey("pk_case_client_version", (caseId, universityID, timestamp))
    def idx = index("idx_case_client_version", (caseId, universityID, version))
  }

  implicit class CaseClientExtensions[C[_]](q: Query[CaseClients, StoredCaseClient, C]) {
    def withClients = q
      .join(ClientDao.clients.table)
      .on(_.universityID === _.universityID)
  }

  case class StoredCaseLink(
    id: UUID,
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    caseNote: UUID,
    teamMember: Usercode,
    version: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredCaseLink] {
    override def atVersion(at: OffsetDateTime): StoredCaseLink = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredCaseLink]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseLinkVersion(
        id,
        linkType,
        outgoingCaseID,
        incomingCaseID,
        caseNote,
        teamMember,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseLinkVersion(
    id: UUID,
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    caseNote: UUID,
    teamMember: Usercode,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseLink]

  trait CommonLinkProperties { self: Table[_] =>
    def linkType = column[CaseLinkType]("link_type")
    def outgoingCaseID = column[UUID]("outgoing_case_id")
    def incomingCaseID = column[UUID]("incoming_case_id")
    def caseNote = column[UUID]("case_note")
    def teamMember = column[Usercode]("team_member")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseLinks(tag: Tag) extends Table[StoredCaseLink](tag, "client_case_link")
    with VersionedTable[StoredCaseLink]
    with CommonLinkProperties {

    def id = column[UUID]("id", O.PrimaryKey)

    override def matchesPrimaryKey(other: StoredCaseLink): Rep[Boolean] = id === other.id

    override def * : ProvenShape[StoredCaseLink] =
      (id, linkType, outgoingCaseID, incomingCaseID, caseNote, teamMember, version).mapTo[StoredCaseLink]
    def pk = primaryKey("pk_case_link", (linkType, outgoingCaseID, incomingCaseID))
    def outgoingFK = foreignKey("fk_case_link_outgoing", outgoingCaseID, cases.table)(_.id)
    def incomingFK = foreignKey("fk_case_link_incoming", incomingCaseID, cases.table)(_.id)
    def outgoingCaseIndex = index("idx_case_link_outgoing", outgoingCaseID)
    def incomingCaseIndex = index("idx_case_link_incoming", incomingCaseID)
  }

  class CaseLinkVersions(tag: Tag) extends Table[StoredCaseLinkVersion](tag, "client_case_link_version")
    with StoredVersionTable[StoredCaseLink]
    with CommonLinkProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseLinkVersion] =
      (id, linkType, outgoingCaseID, incomingCaseID, caseNote, teamMember, version, operation, timestamp, auditUser).mapTo[StoredCaseLinkVersion]
    def pk = primaryKey("pk_case_link_version", (linkType, outgoingCaseID, incomingCaseID, timestamp))
    def idx = index("idx_case_link_version", (linkType, outgoingCaseID, incomingCaseID, version))
  }

  implicit class CaseLinkExtensions[C[_]](q: Query[CaseLinks, StoredCaseLink, C]) {
    def withMember = q
      .join(MemberDao.members.table)
      .on(_.teamMember === _.usercode)
  }

  case class StoredCaseNote(
    id: UUID,
    caseId: UUID,
    noteType: CaseNoteType,
    text: String,
    teamMember: Usercode,
    appointmentId: Option[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredCaseNote] with Created {
    def asCaseNote(member: Member) = CaseNote(
      id,
      noteType,
      text,
      member,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredCaseNote = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredCaseNote]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseNoteVersion(
        id,
        caseId,
        noteType,
        text,
        teamMember,
        appointmentId,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseNoteVersion(
    id: UUID,
    caseId: UUID,
    noteType: CaseNoteType,
    text: String,
    teamMember: Usercode,
    appointmentId: Option[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseNote] with Created

  trait CommonNoteProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def noteType = column[CaseNoteType]("note_type")
    def text = column[String]("text")
    def teamMember = column[Usercode]("team_member")
    def appointmentId = column[Option[UUID]]("appointment_id")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseNotes(tag: Tag) extends Table[StoredCaseNote](tag, "client_case_note")
    with VersionedTable[StoredCaseNote]
    with CommonNoteProperties {
    override def matchesPrimaryKey(other: StoredCaseNote): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)
    def searchableText: Rep[TsVector] = column[TsVector]("text_tsv")

    override def * : ProvenShape[StoredCaseNote] =
      (id, caseId, noteType, text, teamMember, appointmentId, created, version).mapTo[StoredCaseNote]
    def fk = foreignKey("fk_case_note", caseId, cases.table)(_.id)
    def idx = index("idx_case_note", caseId)
    def appointmentFK = foreignKey("fk_case_note_appointment", appointmentId, AppointmentDao.appointments.table)(_.id.?)
    def appointmentIndex = index("idx_case_note_appointment", appointmentId)
  }

  class CaseNoteVersions(tag: Tag) extends Table[StoredCaseNoteVersion](tag, "client_case_note_version")
    with StoredVersionTable[StoredCaseNote]
    with CommonNoteProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseNoteVersion] =
      (id, caseId, noteType, text, teamMember, appointmentId, created, version, operation, timestamp, auditUser).mapTo[StoredCaseNoteVersion]
    def pk = primaryKey("pk_case_note_version", (id, timestamp))
    def idx = index("idx_case_note_version", (id, version))
  }

  implicit class CaseNoteExtensions[C[_]](q: Query[CaseNotes, StoredCaseNote, C]) {
    def withMember = q
      .join(MemberDao.members.table)
      .on(_.teamMember === _.usercode)
  }

  case class StoredCaseDocument(
    id: UUID,
    caseId: UUID,
    documentType: CaseDocumentType,
    fileId: UUID,
    teamMember: Usercode,
    caseNote: UUID,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredCaseDocument] with Created {
    def asCaseDocument(file: UploadedFile, note: CaseNote, member: Member) = CaseDocument(
      id,
      documentType,
      file,
      member,
      note,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredCaseDocument = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredCaseDocument]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseDocumentVersion(
        id,
        caseId,
        documentType,
        fileId,
        teamMember,
        caseNote,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseDocumentVersion(
    id: UUID,
    caseId: UUID,
    documentType: CaseDocumentType,
    fileId: UUID,
    teamMember: Usercode,
    caseNote: UUID,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseDocument] with Created

  trait CommonDocumentProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def documentType = column[CaseDocumentType]("document_type")
    def fileId = column[UUID]("file_id")
    def teamMember = column[Usercode]("team_member")
    def caseNote = column[UUID]("case_note")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseDocuments(tag: Tag) extends Table[StoredCaseDocument](tag, "client_case_document")
    with VersionedTable[StoredCaseDocument]
    with CommonDocumentProperties {
    override def matchesPrimaryKey(other: StoredCaseDocument): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredCaseDocument] =
      (id, caseId, documentType, fileId, teamMember, caseNote, created, version).mapTo[StoredCaseDocument]
    def caseFK = foreignKey("fk_case_document_case", caseId, cases.table)(_.id)
    def fileFK = foreignKey("fk_case_document_file", fileId, UploadedFileDao.uploadedFiles.table)(_.id)
    def caseIndex = index("idx_case_document_case", caseId)
    def fileIndex = index("idx_case_document_file", fileId)
  }

  class CaseDocumentVersions(tag: Tag) extends Table[StoredCaseDocumentVersion](tag, "client_case_document_version")
    with StoredVersionTable[StoredCaseDocument]
    with CommonDocumentProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseDocumentVersion] =
      (id, caseId, documentType, fileId, teamMember, caseNote, created, version, operation, timestamp, auditUser).mapTo[StoredCaseDocumentVersion]
    def pk = primaryKey("pk_case_document_version", (id, timestamp))
    def idx = index("idx_case_document_version", (id, version))
  }

  /**
    * Note that if you set team and member this is treated as an OR, not an AND.
    */
  case class CaseSearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    team: Option[Team] = None,
    member: Option[Usercode] = None,
    caseType: Option[CaseType] = None,
    state: Option[IssueStateFilter] = None
  ) {
    def isEmpty: Boolean = !nonEmpty
    def nonEmpty: Boolean =
      query.exists(_.hasText) ||
      createdAfter.nonEmpty ||
      createdBefore.nonEmpty ||
      team.nonEmpty ||
      member.nonEmpty ||
      caseType.nonEmpty ||
      state.nonEmpty
  }

  case class CaseFilter(
    team: Option[Team] = None,
    owner: Set[Usercode] = Set.empty,
    state: IssueStateFilter = IssueStateFilter.All,
  ) {
    require(team.nonEmpty || owner.nonEmpty, "One of team or owner must be set")

    def withOwners(owners: Set[Usercode]): CaseFilter = copy(owner = owners)
    def withState(state: IssueStateFilter): CaseFilter = copy(state = state)
  }

  object CaseFilter {
    def apply(team: Team): CaseFilter = CaseFilter(team = Some(team))
    def apply(owner: Usercode): CaseFilter = CaseFilter(owner = Set(owner))
    def apply(team: Team, owner: Usercode): CaseFilter = CaseFilter(team = Some(team), owner = Set(owner))
  }

  val lastUpdatedCaseNote =
    caseNotes.table
      .groupBy(_.caseId)
      .map { case (id, n) => (id, n.map(_.version).max) }

}
