package domain.dao

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.CaseDao._
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[CaseDaoImpl])
trait CaseDao {
  def insert(c: Case)(implicit ac: AuditLogContext): DBIO[Case]
  def find(id: UUID): DBIO[Case]
  def find(ids: Set[UUID]): DBIO[Seq[Case]]
  def find(key: IssueKey): DBIO[Case]
  def findByIDQuery(id: UUID): Query[Cases, Case, Seq]
  def findByIDsQuery(ids: Set[UUID]): Query[Cases, Case, Seq]
  def findByKeyQuery(key: IssueKey): Query[Cases, Case, Seq]
  def findByClientQuery(universityID: UniversityID): Query[Cases, Case, Seq]
  def searchQuery(query: CaseSearchQuery): Query[Cases, Case, Seq]
  def update(c: Case, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Case]
  def insertTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseTag]]
  def insertTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[StoredCaseTag]
  def deleteTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[Done]
  def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq]
  def insertClients(clients: Set[CaseClient])(implicit ac: AuditLogContext): DBIO[Seq[CaseClient]]
  def insertClient(client: CaseClient)(implicit ac: AuditLogContext): DBIO[CaseClient]
  def deleteClient(client: CaseClient)(implicit ac: AuditLogContext): DBIO[Done]
  def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq]
  def insertLink(link: StoredCaseLink)(implicit ac: AuditLogContext): DBIO[StoredCaseLink]
  def deleteLink(link: StoredCaseLink, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq]
  def insertNote(note: StoredCaseNote)(implicit ac: AuditLogContext): DBIO[StoredCaseNote]
  def updateNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCaseNote]
  def deleteNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq]
  def insertDocument(document: StoredCaseDocument)(implicit ac: AuditLogContext): DBIO[StoredCaseDocument]
  def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq]
  def listQuery(team: Option[Team], owner: Option[Usercode], state: IssueStateFilter): Query[Cases, Case, Seq]
  def getHistory(id: UUID): DBIO[Seq[CaseVersion]]
  def getTagHistory(caseID: UUID): DBIO[Seq[StoredCaseTagVersion]]
  def getClientHistory(caseID: UUID): DBIO[Seq[CaseClientVersion]]
  def findByOriginalEnquiryQuery(enquiryId: UUID): Query[Cases, Case, Seq]
}

@Singleton
class CaseDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CaseDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(c: Case)(implicit ac: AuditLogContext): DBIO[Case] =
    cases.insert(c)

  override def find(id: UUID): DBIO[Case] =
    findByIDQuery(id).result.head

  override def find(ids: Set[UUID]): DBIO[Seq[Case]] =
    findByIDsQuery(ids).result

  override def find(key: IssueKey): DBIO[Case] =
    findByKeyQuery(key).result.head

  override def findByIDQuery(id: UUID): Query[Cases, Case, Seq] =
    cases.table.filter(_.id === id)

  override def findByIDsQuery(ids: Set[UUID]): Query[Cases, Case, Seq] =
    cases.table.filter(_.id.inSet(ids))

  override def findByKeyQuery(key: IssueKey): Query[Cases, Case, Seq] =
    cases.table.filter(_.key === key)

  override def findByClientQuery(universityID: UniversityID): Query[Cases, Case, Seq] =
    cases.table
      .withClients
      .filter { case (_, client) => client.client === universityID }
      .map { case (c, _) => c }

  override def searchQuery(q: CaseSearchQuery): Query[Cases, Case, Seq] = {
    def queries(c: Cases, n: Rep[Option[CaseNotes]], o: Rep[Option[Owner.Owners]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          val query = plainToTsQuery(queryStr.bind, Some("english"))

          // Need to search CaseNote fields separately otherwise the @+ will stop it matching cases
          // with no notes
          (c.searchableKey @+ c.searchableSubject) @@ query ||
          n.map(_.searchableText) @@ query
        },
        q.createdAfter.map { d => c.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => c.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.team.map { team => c.team.? === team },
        q.member.map { member => o.map(_.userId === member) },
        q.caseType.map { caseType => c.caseType === caseType },
        q.state.flatMap {
          case IssueStateFilter.All => None
          case IssueStateFilter.Open => Some(c.isOpen.?)
          case IssueStateFilter.Closed => Some(!c.isOpen.?)
        }
      ).flatten

    cases.table
      .withNotes
      .joinLeft(Owner.owners.table)
      .on { case ((e, _), o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Case:Owner.EntityType) }
      .filter { case ((c, n), o) => queries(c, n, o).reduce(_ && _) }
      .map { case ((c, _), _) => (c, c.isOpen) }
      .sortBy { case (c, isOpen) => (isOpen.desc, c.created.desc) }
      .distinct
      .map { case (c, _) => c }
  }

  override def update(c: Case, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Case] =
    cases.update(c.copy(version = version))

  override def insertTags(tags: Set[StoredCaseTag])(implicit ac: AuditLogContext): DBIO[Seq[StoredCaseTag]] =
    caseTags.insertAll(tags.toSeq)

  override def insertTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[StoredCaseTag] =
    caseTags.insert(tag)

  override def deleteTag(tag: StoredCaseTag)(implicit ac: AuditLogContext): DBIO[Done] =
    caseTags.delete(tag)

  override def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq] =
    caseTags.table
      .filter(_.caseId.inSet(caseIds))

  override def insertClients(clients: Set[CaseClient])(implicit ac: AuditLogContext): DBIO[Seq[CaseClient]] =
    caseClients.insertAll(clients.toSeq)

  override def insertClient(client: CaseClient)(implicit ac: AuditLogContext): DBIO[CaseClient] =
    caseClients.insert(client)

  override def deleteClient(client: CaseClient)(implicit ac: AuditLogContext): DBIO[Done] =
    caseClients.delete(client)

  override def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq] =
    caseClients.table
      .filter(_.caseId.inSet(caseIds))

  override def insertLink(link: StoredCaseLink)(implicit ac: AuditLogContext): DBIO[StoredCaseLink] =
    caseLinks.insert(link)

  override def deleteLink(link: StoredCaseLink, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseLinks.delete(link.copy(version = version))

  override def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq] =
    caseLinks.table.filter { l => l.outgoingCaseID === caseID || l.incomingCaseID === caseID }

  override def insertNote(note: StoredCaseNote)(implicit ac: AuditLogContext): DBIO[StoredCaseNote] =
    caseNotes.insert(note)

  override def updateNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredCaseNote] =
    caseNotes.update(note.copy(version = version))

  override def deleteNote(note: StoredCaseNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseNotes.delete(note.copy(version = version))

  override def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq] =
    caseNotes.table.filter(_.caseId === caseID)

  override def insertDocument(document: StoredCaseDocument)(implicit ac: AuditLogContext): DBIO[StoredCaseDocument] =
    caseDocuments.insert(document)

  override def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    caseDocuments.delete(document.copy(version = version))

  override def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq] =
    caseDocuments.table.filter(_.caseId === caseID)

  override def listQuery(team: Option[Team], owner: Option[Usercode], state: IssueStateFilter): Query[Cases, Case, Seq] = {
    owner.fold(cases.table.subquery)(u =>
      cases.table
        .join(Owner.owners.table)
        .on((c, o) => c.id === o.entityId && o.entityType === (Owner.EntityType.Case : Owner.EntityType))
        .filter { case (_, o) => o.userId === u }
        .map { case (e, _) => e }
    ).filter(c => {
      val teamFilter = team.fold(true.bind)(c.team === _)
      val stateFilter = state match {
        case IssueStateFilter.Open => c.isOpen
        case IssueStateFilter.Closed => !c.isOpen
        case IssueStateFilter.All => true.bind
      }
      teamFilter && stateFilter
    })
  }

  override def getHistory(id: UUID): DBIO[Seq[CaseVersion]] = {
    cases.versionsTable
      .filter(c =>
        c.id === id && (
          c.operation === (DatabaseOperation.Insert:DatabaseOperation) ||
          c.operation === (DatabaseOperation.Update:DatabaseOperation)
        )
      )
      .sortBy(_.timestamp)
      .result
  }

  override def getTagHistory(caseID: UUID): DBIO[Seq[StoredCaseTagVersion]] = {
    caseTags.versionsTable.filter(t => t.caseId === caseID).result
  }

  override def getClientHistory(caseID: UUID): DBIO[Seq[CaseClientVersion]] = {
    caseClients.versionsTable.filter(c => c.caseId === caseID).result
  }

  override def findByOriginalEnquiryQuery(enquiryId: UUID): Query[Cases, Case, Seq] = {
    cases.table.filter(c => c.originalEnquiry.map(_ === enquiryId))
  }
}

object CaseDao {

  val cases: VersionedTableQuery[Case, CaseVersion, Cases, CaseVersions] =
    VersionedTableQuery(TableQuery[Cases], TableQuery[CaseVersions])

  val caseTags: VersionedTableQuery[StoredCaseTag, StoredCaseTagVersion, CaseTags, CaseTagVersions] =
    VersionedTableQuery(TableQuery[CaseTags], TableQuery[CaseTagVersions])

  val caseClients: VersionedTableQuery[CaseClient, CaseClientVersion, CaseClients, CaseClientVersions] =
    VersionedTableQuery(TableQuery[CaseClients], TableQuery[CaseClientVersions])

  val caseLinks: VersionedTableQuery[StoredCaseLink, StoredCaseLinkVersion, CaseLinks, CaseLinkVersions] =
    VersionedTableQuery(TableQuery[CaseLinks], TableQuery[CaseLinkVersions])

  val caseNotes: VersionedTableQuery[StoredCaseNote, StoredCaseNoteVersion, CaseNotes, CaseNoteVersions] =
    VersionedTableQuery(TableQuery[CaseNotes], TableQuery[CaseNoteVersions])

  val caseDocuments: VersionedTableQuery[StoredCaseDocument, StoredCaseDocumentVersion, CaseDocuments, CaseDocumentVersions] =
    VersionedTableQuery(TableQuery[CaseDocuments], TableQuery[CaseDocumentVersions])

  case class Case(
    id: Option[UUID],
    key: Option[IssueKey],
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
    cause: CaseCause
  ) extends Versioned[Case] {
    override def atVersion(at: OffsetDateTime): Case = copy(version = at)
    override def storedVersion[B <: StoredVersion[Case]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      CaseVersion(
        id.get,
        key.get,
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
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  object Case {
    def tupled = (apply _).tupled

    val SubjectMaxLength = 200

    /**
      * This might not be a way we should do things, but if we did want a service to return
      * everything we need to display
      */
    case class FullyJoined(
      clientCase: Case,
      clients: Set[UniversityID],
      tags: Set[CaseTag],
      notes: Seq[CaseNote],
      documents: Seq[CaseDocument],
      outgoingCaseLinks: Seq[CaseLink],
      incomingCaseLinks: Seq[CaseLink],
      messages: CaseMessages
    )
  }

  case class CaseMessages(data: Seq[MessageRender]) {
    lazy val byClient: Map[UniversityID, Seq[MessageRender]] = data.groupBy(_.message.client)
    lazy val teamMembers: Set[Usercode] = data.flatMap(_.message.teamMember).toSet
  }

  case class CaseVersion(
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

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[Case]

  trait CommonProperties { self: Table[_] =>
    def key = column[IssueKey]("case_key")
    def searchableKey = toTsVector(key.asColumnOf[String], Some("english"))
    def subject = column[String]("subject")
    def searchableSubject = toTsVector(subject, Some("english"))
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
  }

  class Cases(tag: Tag) extends Table[Case](tag, "client_case")
    with VersionedTable[Case]
    with CommonProperties {
    override def matchesPrimaryKey(other: Case): Rep[Boolean] = id === other.id.orNull
    def id = column[UUID]("id", O.PrimaryKey)

    def isOpen = state === (IssueState.Open : IssueState) || state === (IssueState.Reopened : IssueState)

    override def * : ProvenShape[Case] =
      (id.?, key.?, subject, created, team, version, state, incidentDate, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause).mapTo[Case]
    def idx = index("idx_client_case_key", key, unique = true)
  }

  class CaseVersions(tag: Tag) extends Table[CaseVersion](tag, "client_case_version")
    with StoredVersionTable[Case]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[CaseVersion] =
      (id, key, subject, created, team, version, state, incidentDate, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause, operation, timestamp, auditUser).mapTo[CaseVersion]
  }

  implicit class CaseExtensions[C[_]](val q: Query[Cases, Case, C]) extends AnyVal {
    def withClients = q
      .join(caseClients.table)
      .on(_.id === _.caseId)
    def withNotes = q
      .joinLeft(caseNotes.table)
      .on(_.id === _.caseId)
    def withMessages = q
      .joinLeft(Message.messages.table.withUploadedFiles)
      .on { case (c, (m, _)) =>
        c.id === m.ownerId && m.ownerType === (MessageOwner.Case: MessageOwner)
      }

    def withLastUpdated = q
      .joinLeft(Message.messages.table)
      .on((c, m) =>
        m.id in Message.messages.table
          .filter(m => m.ownerId === c.id && m.ownerType === (MessageOwner.Case: MessageOwner))
          .sortBy(_.created.reverse)
          .take(1)
          .map(_.id)
      )
      .joinLeft(caseNotes.table)
      .on { case ((c, _), n) =>
        n.id in caseNotes.table
          .filter(_.caseId === c.id)
          .sortBy(_.created.reverse)
          .take(1)
          .map(_.id)
      }
      .map { case ((c, m), n) => (c, m.map(_.created), n.map(_.created)) }
  }

  case class StoredCaseTag(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = OffsetDateTime.now()
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
    version: OffsetDateTime = OffsetDateTime.now(),
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

  case class CaseClient(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[CaseClient] {
    override def atVersion(at: OffsetDateTime): CaseClient = copy(version = at)
    override def storedVersion[B <: StoredVersion[CaseClient]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      CaseClientVersion(
        caseId,
        client,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class CaseClientVersion(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[CaseClient]

  trait CommonClientProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def client = column[UniversityID]("university_id")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseClients(tag: Tag) extends Table[CaseClient](tag, "client_case_client")
    with VersionedTable[CaseClient]
    with CommonClientProperties {
    override def matchesPrimaryKey(other: CaseClient): Rep[Boolean] =
      caseId === other.caseId && client === other.client

    override def * : ProvenShape[CaseClient] =
      (caseId, client, version).mapTo[CaseClient]
    def pk = primaryKey("pk_case_client", (caseId, client))
    def fk = foreignKey("fk_case_client", caseId, cases.table)(_.id)
    def caseIndex = index("idx_case_client", caseId)
    def clientIndex = index("idx_case_client_university_id", client)
  }

  class CaseClientVersions(tag: Tag) extends Table[CaseClientVersion](tag, "client_case_client_version")
    with StoredVersionTable[CaseClient]
    with CommonClientProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[CaseClientVersion] =
      (caseId, client, version, operation, timestamp, auditUser).mapTo[CaseClientVersion]
    def pk = primaryKey("pk_case_client_version", (caseId, client, timestamp))
    def idx = index("idx_case_client_version", (caseId, client, version))
  }

  case class StoredCaseLink(
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[StoredCaseLink] {
    override def atVersion(at: OffsetDateTime): StoredCaseLink = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredCaseLink]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredCaseLinkVersion(
        linkType,
        outgoingCaseID,
        incomingCaseID,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredCaseLinkVersion(
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseLink]

  trait CommonLinkProperties { self: Table[_] =>
    def linkType = column[CaseLinkType]("link_type")
    def outgoingCaseID = column[UUID]("outgoing_case_id")
    def incomingCaseID = column[UUID]("incoming_case_id")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseLinks(tag: Tag) extends Table[StoredCaseLink](tag, "client_case_link")
    with VersionedTable[StoredCaseLink]
    with CommonLinkProperties {
    override def matchesPrimaryKey(other: StoredCaseLink): Rep[Boolean] =
      linkType === other.linkType && outgoingCaseID === other.outgoingCaseID && incomingCaseID === other.incomingCaseID

    override def * : ProvenShape[StoredCaseLink] =
      (linkType, outgoingCaseID, incomingCaseID, version).mapTo[StoredCaseLink]
    def pk = primaryKey("pk_case_link", (linkType, outgoingCaseID, incomingCaseID))
    def outgoingFK = foreignKey("fk_case_link_outgoing", outgoingCaseID, cases.table)(_.id)
    def incomingFK = foreignKey("fk_case_link_incoming", incomingCaseID, cases.table)(_.id)
    def outgoingCaseIndex = index("idx_case_link_outgoing", outgoingCaseID)
    def incomingCaseIndex = index("idx_case_link_incoming", incomingCaseID)
  }

  class CaseLinkVersions(tag: Tag) extends Table[StoredCaseLinkVersion](tag, "client_case_link_version")
    with StoredVersionTable[StoredCaseLink]
    with CommonLinkProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseLinkVersion] =
      (linkType, outgoingCaseID, incomingCaseID, version, operation, timestamp, auditUser).mapTo[StoredCaseLinkVersion]
    def pk = primaryKey("pk_case_link_version", (linkType, outgoingCaseID, incomingCaseID, timestamp))
    def idx = index("idx_case_link_version", (linkType, outgoingCaseID, incomingCaseID, version))
  }

  case class StoredCaseNote(
    id: UUID,
    caseId: UUID,
    noteType: CaseNoteType,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredCaseNote] {
    def asCaseNote = CaseNote(
      id,
      noteType,
      text,
      teamMember,
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
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseNote]

  trait CommonNoteProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def noteType = column[CaseNoteType]("note_type")
    def text = column[String]("text")
    def searchableText = toTsVector(text, Some("english"))
    def teamMember = column[Usercode]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseNotes(tag: Tag) extends Table[StoredCaseNote](tag, "client_case_note")
    with VersionedTable[StoredCaseNote]
    with CommonNoteProperties {
    override def matchesPrimaryKey(other: StoredCaseNote): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredCaseNote] =
      (id, caseId, noteType, text, teamMember, created, version).mapTo[StoredCaseNote]
    def fk = foreignKey("fk_case_note", caseId, cases.table)(_.id)
    def idx = index("idx_case_note", caseId)
  }

  class CaseNoteVersions(tag: Tag) extends Table[StoredCaseNoteVersion](tag, "client_case_note_version")
    with StoredVersionTable[StoredCaseNote]
    with CommonNoteProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredCaseNoteVersion] =
      (id, caseId, noteType, text, teamMember, created, version, operation, timestamp, auditUser).mapTo[StoredCaseNoteVersion]
    def pk = primaryKey("pk_case_note_version", (id, timestamp))
    def idx = index("idx_case_note_version", (id, version))
  }

  case class StoredCaseDocument(
    id: UUID,
    caseId: UUID,
    documentType: CaseDocumentType,
    fileId: UUID,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredCaseDocument] {
    def asCaseDocument(file: UploadedFile) = CaseDocument(
      id,
      documentType,
      file,
      teamMember,
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
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredCaseDocument]

  trait CommonDocumentProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def documentType = column[CaseDocumentType]("document_type")
    def fileId = column[UUID]("file_id")
    def teamMember = column[Usercode]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseDocuments(tag: Tag) extends Table[StoredCaseDocument](tag, "client_case_document")
    with VersionedTable[StoredCaseDocument]
    with CommonDocumentProperties {
    override def matchesPrimaryKey(other: StoredCaseDocument): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredCaseDocument] =
      (id, caseId, documentType, fileId, teamMember, created, version).mapTo[StoredCaseDocument]
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
      (id, caseId, documentType, fileId, teamMember, created, version, operation, timestamp, auditUser).mapTo[StoredCaseDocumentVersion]
    def pk = primaryKey("pk_case_document_version", (id, timestamp))
    def idx = index("idx_case_document_version", (id, version))
  }

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
}
