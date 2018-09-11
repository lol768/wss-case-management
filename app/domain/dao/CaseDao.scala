package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.CaseDao._
import enumeratum.{EnumEntry, PlayEnum}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import ExtendedPostgresProfile.api._
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[CaseDaoImpl])
trait CaseDao {
  def insert(c: Case): DBIO[Case]
  def find(id: UUID): DBIO[Case]
  def find(key: IssueKey): DBIO[Case]
  def findByIDQuery(id: UUID): Query[Cases, Case, Seq]
  def findByKeyQuery(key: IssueKey): Query[Cases, Case, Seq]
  def findByClientQuery(universityID: UniversityID): Query[Cases, Case, Seq]
  def searchQuery(query: String): Query[Cases, Case, Seq]
  def update(c: Case, version: OffsetDateTime): DBIO[Case]
  def insertTags(tags: Set[StoredCaseTag]): DBIO[Seq[StoredCaseTag]]
  def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag]
  def deleteTag(tag: StoredCaseTag): DBIO[Done]
  def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq]
  def insertClients(clients: Set[CaseClient]): DBIO[Seq[CaseClient]]
  def insertClient(client: CaseClient): DBIO[CaseClient]
  def deleteClient(client: CaseClient): DBIO[Done]
  def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq]
  def insertLink(link: StoredCaseLink): DBIO[StoredCaseLink]
  def deleteLink(link: StoredCaseLink, version: OffsetDateTime): DBIO[Done]
  def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq]
  def insertNote(note: StoredCaseNote): DBIO[StoredCaseNote]
  def updateNote(note: StoredCaseNote, version: OffsetDateTime): DBIO[StoredCaseNote]
  def deleteNote(note: StoredCaseNote, version: OffsetDateTime): DBIO[Done]
  def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq]
  def insertDocument(document: StoredCaseDocument): DBIO[StoredCaseDocument]
  def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime): DBIO[Done]
  def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq]
  def listQuery(team: Option[Team], owner: Option[Usercode], state: CaseStateFilter): Query[Cases, Case, Seq]
}

@Singleton
class CaseDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CaseDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(c: Case): DBIO[Case] =
    cases.insert(c)

  override def find(id: UUID): DBIO[Case] =
    findByIDQuery(id).result.head

  override def find(key: IssueKey): DBIO[Case] =
    findByKeyQuery(key).result.head

  def findByIDQuery(id: UUID): Query[Cases, Case, Seq] =
    cases.table.filter(_.id === id)

  def findByKeyQuery(key: IssueKey): Query[Cases, Case, Seq] =
    cases.table.filter(_.key === key)

  def findByClientQuery(universityID: UniversityID): Query[Cases, Case, Seq] =
    cases.table
      .withClients
      .filter { case (_, client) => client.client === universityID }
      .map { case (c, _) => c }

  override def searchQuery(queryStr: String): Query[Cases, Case, Seq] = {
    val query = toTsQuery(queryStr.bind, Some("english"))

    cases.table
      .withNotes
      .filter { case (c, n) =>
        c.searchableSubject @@ query ||
        n.map(_.searchableText @@ query)
      }
      .map { case (c, _) => c }
      .distinct
  }

  override def update(c: Case, version: OffsetDateTime): DBIO[Case] =
    cases.update(c.copy(version = version))

  override def insertTags(tags: Set[StoredCaseTag]): DBIO[Seq[StoredCaseTag]] =
    caseTags.insertAll(tags.toSeq)

  override def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag] =
    caseTags.insert(tag)

  override def deleteTag(tag: StoredCaseTag): DBIO[Done] =
    caseTags.delete(tag)

  override def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq] =
    caseTags.table
      .filter(_.caseId.inSet(caseIds))

  override def insertClients(clients: Set[CaseClient]): DBIO[Seq[CaseClient]] =
    caseClients.insertAll(clients.toSeq)

  override def insertClient(client: CaseClient): DBIO[CaseClient] =
    caseClients.insert(client)

  override def deleteClient(client: CaseClient): DBIO[Done] =
    caseClients.delete(client)

  override def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq] =
    caseClients.table
      .filter(_.caseId.inSet(caseIds))

  override def insertLink(link: StoredCaseLink): DBIO[StoredCaseLink] =
    caseLinks.insert(link)

  override def deleteLink(link: StoredCaseLink, version: OffsetDateTime): DBIO[Done] =
    caseLinks.delete(link.copy(version = version))

  override def findLinksQuery(caseID: UUID): Query[CaseLinks, StoredCaseLink, Seq] =
    caseLinks.table.filter { l => l.outgoingCaseID === caseID || l.incomingCaseID === caseID }

  override def insertNote(note: StoredCaseNote): DBIO[StoredCaseNote] =
    caseNotes.insert(note)

  override def updateNote(note: StoredCaseNote, version: OffsetDateTime): DBIO[StoredCaseNote] =
    caseNotes.update(note.copy(version = version))

  override def deleteNote(note: StoredCaseNote, version: OffsetDateTime): DBIO[Done] =
    caseNotes.delete(note.copy(version = version))

  override def findNotesQuery(caseID: UUID): Query[CaseNotes, StoredCaseNote, Seq] =
    caseNotes.table.filter(_.caseId === caseID)

  override def insertDocument(document: StoredCaseDocument): DBIO[StoredCaseDocument] =
    caseDocuments.insert(document)

  override def deleteDocument(document: StoredCaseDocument, version: OffsetDateTime): DBIO[Done] =
    caseDocuments.delete(document.copy(version = version))

  override def findDocumentsQuery(caseID: UUID): Query[CaseDocuments, StoredCaseDocument, Seq] =
    caseDocuments.table.filter(_.caseId === caseID)

  override def listQuery(team: Option[Team], owner: Option[Usercode], state: CaseStateFilter): Query[Cases, Case, Seq] = {
    owner.fold(cases.table.subquery)(u =>
      cases.table
        .join(Owner.owners.table)
        .on((c, o) => c.id === o.entityId && o.entityType === (Owner.EntityType.Case : Owner.EntityType))
        .filter { case (_, o) => o.userId === u }
        .map { case (e, _) => e }
    ).filter(c => {
      val teamFilter = team.fold(true.bind)(c.team === _)
      val stateFilter = state match {
        case CaseStateFilter.Open => c.isOpen
        case CaseStateFilter.Closed => !c.isOpen
        case CaseStateFilter.All => true.bind
      }
      teamFilter && stateFilter
    })
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
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause
  ) extends Versioned[Case] {
    override def atVersion(at: OffsetDateTime): Case = copy(version = at)
    override def storedVersion[B <: StoredVersion[Case]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      CaseVersion(
        id.get,
        key.get,
        subject,
        created,
        incidentDate,
        team,
        version,
        state,
        onCampus,
        notifiedPolice,
        notifiedAmbulance,
        notifiedFire,
        originalEnquiry,
        caseType,
        cause,
        operation,
        timestamp
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
      //    relatedAppointments: Seq[Appointment],
      outgoingCaseLinks: Seq[CaseLink],
      incomingCaseLinks: Seq[CaseLink],
      messages: Seq[MessageData]
    )
  }

  case class CaseVersion(
    id: UUID,
    key: IssueKey,
    subject: String,
    created: OffsetDateTime,
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
  ) extends StoredVersion[Case]

  trait CommonProperties { self: Table[_] =>
    def key = column[IssueKey]("case_key")
    def subject = column[String]("subject")
    def searchableSubject = toTsVector(subject, Some("english"))
    def created = column[OffsetDateTime]("created_utc")
    def incidentDate = column[OffsetDateTime]("incident_date_utc")
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def state = column[IssueState]("state")
    def onCampus = column[Boolean]("on_campus")
    def notifiedPolice = column[Boolean]("notified_police")
    def notifiedAmbulance = column[Boolean]("notified_ambulance")
    def notifiedFire = column[Boolean]("notified_fire")
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
      (id.?, key.?, subject, created, incidentDate, team, version, state, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause).mapTo[Case]
    def idx = index("idx_client_case_key", key, unique = true)
  }

  class CaseVersions(tag: Tag) extends Table[CaseVersion](tag, "client_case_version")
    with StoredVersionTable[Case]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[CaseVersion] =
      (id, key, subject, created, incidentDate, team, version, state, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause, operation, timestamp).mapTo[CaseVersion]
  }

  implicit class CaseExtensions[C[_]](q: Query[Cases, Case, C]) {
    def withClients = q
      .join(caseClients.table)
      .on(_.id === _.caseId)
    def withNotes = q
      .joinLeft(caseNotes.table)
      .on(_.id === _.caseId)
    def withMessages = q
      .joinLeft(Message.messages.table)
      .on { (c, m) =>
        c.id === m.ownerId && m.ownerType === (MessageOwner.Case: MessageOwner)
      }
    def withMessagesAndNotes =
      withMessages
        .joinLeft(caseNotes.table)
        .on { case ((c, _), n) => c.id === n.caseId }
        .map { case ((c, m), n) => (c, m, n) }
  }

  case class StoredCaseTag(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[StoredCaseTag] {
    override def atVersion(at: OffsetDateTime): StoredCaseTag = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredCaseTag]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseTagVersion(
        caseId,
        caseTag,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class StoredCaseTagVersion(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
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

    override def * : ProvenShape[StoredCaseTagVersion] =
      (caseId, caseTag, version, operation, timestamp).mapTo[StoredCaseTagVersion]
    def pk = primaryKey("pk_case_tag_version", (caseId, caseTag, timestamp))
    def idx = index("idx_case_tag_version", (caseId, caseTag, version))
  }

  case class CaseClient(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[CaseClient] {
    override def atVersion(at: OffsetDateTime): CaseClient = copy(version = at)
    override def storedVersion[B <: StoredVersion[CaseClient]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      CaseClientVersion(
        caseId,
        client,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class CaseClientVersion(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
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

    override def * : ProvenShape[CaseClientVersion] =
      (caseId, client, version, operation, timestamp).mapTo[CaseClientVersion]
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

    override def storedVersion[B <: StoredVersion[StoredCaseLink]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseLinkVersion(
        linkType,
        outgoingCaseID,
        incomingCaseID,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class StoredCaseLinkVersion(
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
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

    override def * : ProvenShape[StoredCaseLinkVersion] =
      (linkType, outgoingCaseID, incomingCaseID, version, operation, timestamp).mapTo[StoredCaseLinkVersion]
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

    override def storedVersion[B <: StoredVersion[StoredCaseNote]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseNoteVersion(
        id,
        caseId,
        noteType,
        text,
        teamMember,
        created,
        version,
        operation,
        timestamp
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
    timestamp: OffsetDateTime
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

    override def * : ProvenShape[StoredCaseNoteVersion] =
      (id, caseId, noteType, text, teamMember, created, version, operation, timestamp).mapTo[StoredCaseNoteVersion]
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

    override def storedVersion[B <: StoredVersion[StoredCaseDocument]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseDocumentVersion(
        id,
        caseId,
        documentType,
        fileId,
        teamMember,
        created,
        version,
        operation,
        timestamp
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
    timestamp: OffsetDateTime
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

    override def * : ProvenShape[StoredCaseDocumentVersion] =
      (id, caseId, documentType, fileId, teamMember, created, version, operation, timestamp).mapTo[StoredCaseDocumentVersion]
    def pk = primaryKey("pk_case_document_version", (id, timestamp))
    def idx = index("idx_case_document_version", (id, version))
  }

  sealed trait CaseStateFilter extends EnumEntry
  object CaseStateFilter extends PlayEnum[CaseStateFilter] {
    case object Open extends CaseStateFilter
    case object Closed extends CaseStateFilter
    case object All extends CaseStateFilter

    val values: immutable.IndexedSeq[CaseStateFilter] = findValues
  }
}
