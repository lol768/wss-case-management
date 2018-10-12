package domain.dao

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.IssueState.{Open, Reopened}
import domain._
import domain.dao.EnquiryDao._
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

@ImplementedBy(classOf[EnquiryDaoImpl])
trait EnquiryDao {
  def insert(enquiry: StoredEnquiry)(implicit ac: AuditLogContext): DBIO[StoredEnquiry]
  def update(enquiry: StoredEnquiry, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredEnquiry]
  def insertNote(note: StoredEnquiryNote)(implicit ac: AuditLogContext): DBIO[StoredEnquiryNote]
  def findByIDQuery(id: UUID): Query[Enquiries, StoredEnquiry, Seq]
  def findByIDsQuery(ids: Set[UUID]): Query[Enquiries, StoredEnquiry, Seq]
  def findByKeyQuery(key: IssueKey): Query[Enquiries, StoredEnquiry, Seq]
  def findByClientQuery(client: UniversityID): Query[Enquiries, StoredEnquiry, Seq]
  def findOpenQuery(team: Team): Query[Enquiries, StoredEnquiry, Seq]
  def findOpenQuery(owner: Usercode): Query[Enquiries, StoredEnquiry, Seq]
  def findClosedQuery(team: Team): Query[Enquiries, StoredEnquiry, Seq]
  def findClosedQuery(owner: Usercode): Query[Enquiries, StoredEnquiry, Seq]
  def findNotesQuery(enquiryIDs: Set[UUID]): Query[EnquiryNotes, StoredEnquiryNote, Seq]
  def searchQuery(query: EnquirySearchQuery): Query[Enquiries, StoredEnquiry, Seq]
}

@Singleton
class EnquiryDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  messageDao: MessageDao
)(implicit ec: ExecutionContext)
  extends EnquiryDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(enquiry: StoredEnquiry)(implicit ac: AuditLogContext): DBIO[StoredEnquiry] =
    enquiries += enquiry

  override def update(enquiry: StoredEnquiry, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredEnquiry] =
    enquiries.update(enquiry.copy(version = version))

  override def insertNote(note: StoredEnquiryNote)(implicit ac: AuditLogContext): DBIO[StoredEnquiryNote] =
    enquiryNotes += note

  override def findByIDQuery(id: UUID): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table.filter(_.id === id)

  override def findByIDsQuery(ids: Set[UUID]): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table.filter(_.id.inSet(ids))

  override def findByKeyQuery(key: IssueKey): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table.filter(_.key === key)

  override def findByClientQuery(client: UniversityID): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table.filter(_.universityId === client)

  override def findOpenQuery(team: Team): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table
      .filter(e => e.isOpen && e.team === team)

  override def findOpenQuery(owner: Usercode): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table
      .join(Owner.owners.table)
      .on((e, o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType))
      .filter { case (e, o) => e.isOpen && o.userId === owner }
      .map { case (e, _) => e }

  override def findClosedQuery(team: Team): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table
      .filter(e => !e.isOpen && e.team === team)

  override def findClosedQuery(owner: Usercode): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table
      .join(Owner.owners.table)
      .on((e, o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType))
      .filter { case (e, o) => !e.isOpen && o.userId === owner }
      .map { case (e, _) => e }

  override def findNotesQuery(enquiryIDs: Set[UUID]): Query[EnquiryNotes, StoredEnquiryNote, Seq] =
    enquiryNotes.table.filter(_.enquiryID.inSet(enquiryIDs))

  override def searchQuery(q: EnquirySearchQuery): Query[Enquiries, StoredEnquiry, Seq] = {
    def queries(e: Enquiries, m: Rep[Option[Message.Messages]], f: Rep[Option[UploadedFileDao.UploadedFiles]], o: Rep[Option[Owner.Owners]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          val query = plainToTsQuery(queryStr.bind, Some("english"))

          // Need to search UploadedFile fields separately otherwise the @+ will stop it matching messages
          // with no file
          (e.searchableKey @+ e.searchableSubject @+ m.map(_.searchableText)) @@ query ||
          f.map(_.searchableFileName) @@ query
        },
        q.createdAfter.map { d => e.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => e.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.team.map { team => e.team.? === team },
        q.member.map { member => o.map(_.userId === member) },
        q.state.flatMap {
          case IssueStateFilter.All => None
          case IssueStateFilter.Open => Some(e.isOpen.?)
          case IssueStateFilter.Closed => Some(!e.isOpen.?)
        }
      ).flatten

    enquiries.table
      .withMessages
      .joinLeft(Owner.owners.table)
      .on { case ((e, _), o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType) }
      .filter { case ((e, mf), o) => queries(e, mf.map(_._1), mf.flatMap(_._2), o).reduce(_ && _) }
      .map { case ((e, _), _) => (e, e.isOpen) }
      .sortBy { case (e, isOpen) => (isOpen.desc, e.created.desc) }
      .distinct
      .map { case (e, _) => e }
  }

}

object EnquiryDao {

  val enquiries: VersionedTableQuery[StoredEnquiry, StoredEnquiryVersion, Enquiries, EnquiryVersions] =
    VersionedTableQuery(TableQuery[Enquiries], TableQuery[EnquiryVersions])

  val enquiryNotes: VersionedTableQuery[StoredEnquiryNote, StoredEnquiryNoteVersion, EnquiryNotes, EnquiryNoteVersions] =
    VersionedTableQuery(TableQuery[EnquiryNotes], TableQuery[EnquiryNoteVersions])

  case class StoredEnquiry(
    id: UUID,
    key: IssueKey,
    universityID: UniversityID,
    subject: String,
    team: Team,
    state: IssueState = Open,
    version: OffsetDateTime = OffsetDateTime.now(),
    created: OffsetDateTime = OffsetDateTime.now(),
  ) extends Versioned[StoredEnquiry] {
    override def atVersion(at: OffsetDateTime): StoredEnquiry = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredEnquiry]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredEnquiryVersion(
        id,
        key,
        universityID,
        subject,
        team,
        state,
        version,
        created,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def asEnquiry(client: Client) = Enquiry(
      id = Some(id),
      key = key,
      client = client,
      subject = subject,
      team = team,
      state = state,
      lastUpdated = version,
      created = created
    )
  }

  case class StoredEnquiryVersion(
    id: UUID,
    key: IssueKey,
    universityID: UniversityID,
    subject: String,
    team: Team,
    state: IssueState,
    version: OffsetDateTime = OffsetDateTime.now(),
    created: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredEnquiry]

  sealed trait CommonEnquiryProperties {
    self: Table[_] =>

    def key = column[IssueKey]("enquiry_key")
    def searchableKey = toTsVector(key.asColumnOf[String], Some("english"))
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def universityId = column[UniversityID]("university_id")
    def subject = column[String]("subject")
    def searchableSubject = toTsVector(subject, Some("english"))
    def state = column[IssueState]("state")
    def created = column[OffsetDateTime]("created_utc")
  }

  class Enquiries(tag: Tag) extends Table[StoredEnquiry](tag, "enquiry") with VersionedTable[StoredEnquiry] with CommonEnquiryProperties {
    override def matchesPrimaryKey(other: StoredEnquiry): Rep[Boolean] = id === other.id

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id, key, universityId, subject, team, state, version, created).mapTo[StoredEnquiry]
    def idx = index("idx_enquiry_key", key, unique = true)

    def isOpen = state === (Open : IssueState) || state === (Reopened : IssueState)
  }

  class EnquiryVersions(tag: Tag) extends Table[StoredEnquiryVersion](tag, "enquiry_version") with StoredVersionTable[StoredEnquiry] with CommonEnquiryProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (id, key, universityId, subject, team, state, version, created, operation, timestamp, auditUser).mapTo[StoredEnquiryVersion]
    def pk = primaryKey("pk_enquiryversions", (id, timestamp))
    def idx = index("idx_enquiryversions", (id, version))
  }

  implicit class EnquiryExtensions[C[_]](q: Query[Enquiries, StoredEnquiry, C]) {
    def withMessages = q
      .joinLeft(Message.messages.table.withUploadedFiles)
      .on { case (e, (m, _)) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
    def withNotes = q
      .joinLeft(enquiryNotes.table)
      .on { case (e, n) => e.id === n.enquiryID }
    def withClient = q
      .join(ClientDao.clients.table)
      .on { case (e, c) => e.universityId === c.universityID }
      .map { case (e, c) => (e, c) }
    def withClientAndMessages = q
      .withClient
      .joinLeft(Message.messages.table.withUploadedFiles)
      .on { case ((e, _), (m, _)) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
      .map { case ((e, c), mf) => (e, c, mf) }
  }

  case class StoredEnquiryNote(
    id: UUID,
    enquiryID: UUID,
    noteType: EnquiryNoteType,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredEnquiryNote] {
    def asEnquiryNote = EnquiryNote(
      id,
      noteType,
      text,
      teamMember,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredEnquiryNote = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredEnquiryNote]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredEnquiryNoteVersion(
        id,
        enquiryID,
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

  case class StoredEnquiryNoteVersion(
    id: UUID,
    enquiryID: UUID,
    noteType: EnquiryNoteType,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredEnquiryNote]

  trait CommonNoteProperties { self: Table[_] =>
    def enquiryID = column[UUID]("enquiry_id")
    def noteType = column[EnquiryNoteType]("note_type")
    def text = column[String]("text")
    def searchableText = toTsVector(text, Some("english"))
    def teamMember = column[Usercode]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class EnquiryNotes(tag: Tag) extends Table[StoredEnquiryNote](tag, "enquiry_note")
    with VersionedTable[StoredEnquiryNote]
    with CommonNoteProperties {
    override def matchesPrimaryKey(other: StoredEnquiryNote): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredEnquiryNote] =
      (id, enquiryID, noteType, text, teamMember, created, version).mapTo[StoredEnquiryNote]
    def fk = foreignKey("fk_enquiry_note", enquiryID, enquiries.table)(_.id)
    def idx = index("idx_enquiry_note", enquiryID)
  }

  class EnquiryNoteVersions(tag: Tag) extends Table[StoredEnquiryNoteVersion](tag, "enquiry_note_version")
    with StoredVersionTable[StoredEnquiryNote]
    with CommonNoteProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredEnquiryNoteVersion] =
      (id, enquiryID, noteType, text, teamMember, created, version, operation, timestamp, auditUser).mapTo[StoredEnquiryNoteVersion]
    def pk = primaryKey("pk_enquiry_note_version", (id, timestamp))
    def idx = index("idx_enquiry_note_version", (id, version))
  }

  case class EnquirySearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    team: Option[Team] = None,
    member: Option[Usercode] = None,
    state: Option[IssueStateFilter] = None
  ) {
    def isEmpty: Boolean = !nonEmpty
    def nonEmpty: Boolean =
      query.exists(_.hasText) ||
        createdAfter.nonEmpty ||
        createdBefore.nonEmpty ||
        team.nonEmpty ||
        member.nonEmpty ||
        state.nonEmpty
  }
}
