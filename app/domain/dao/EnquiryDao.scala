package domain.dao

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

import com.github.tminglei.slickpg.TsVector
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.IssueState.{Open, Reopened}
import domain._
import domain.dao.ClientDao.StoredClient.Clients
import domain.dao.EnquiryDao._
import domain.dao.MemberDao.StoredMember.Members
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.{AuditLogContext, TeamMetrics}
import slick.jdbc.GetResult
import slick.lifted.ProvenShape
import warwick.core.helpers.JavaTime
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
  def findByFilterQuery(filter: EnquiryFilter, state: IssueStateFilter): Query[Enquiries, StoredEnquiry, Seq]
  def findNotesQuery(enquiryIDs: Set[UUID]): Query[EnquiryNotes, StoredEnquiryNote, Seq]
  def searchQuery(query: EnquirySearchQuery): Query[Enquiries, StoredEnquiry, Seq]
  def getLastUpdatedForClients(clients: Set[UniversityID]): DBIO[Seq[(UniversityID, Option[OffsetDateTime])]]
  def findByStateQuery(state: IssueStateFilter): Query[Enquiries, StoredEnquiry, Seq]
  def getFirstEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Team): DBIO[Seq[StoredEnquiry]]
  def countFirstEnquiriesByTeam(start: OffsetDateTime, end: OffsetDateTime): DBIO[Seq[TeamMetrics]]
  def getEnquiryHistory(id: UUID): DBIO[Seq[StoredEnquiryVersion]]
}

@Singleton
class EnquiryDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  messageDao: MessageDao
)(implicit ec: ExecutionContext)
  extends EnquiryDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

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

  def findByFilterQuery(filter: EnquiryFilter, state: IssueStateFilter): Query[Enquiries, StoredEnquiry, Seq] =
    Option(filter.owner).filter(_.nonEmpty).fold(enquiries.table.subquery)(usercodes =>
      enquiries.table
        .join(Owner.owners.table)
        .on((e, o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry: Owner.EntityType))
        .filter { case (_, o) => o.userId.inSet(usercodes) }
        .map { case (e, _) => e }
    ).filter(e => {
      val teamFilter = filter.team.fold(true.bind)(e.team === _)
      val stateFilter = state match {
        case IssueStateFilter.Open => e.isOpen
        case IssueStateFilter.Closed => !e.isOpen
        case IssueStateFilter.All => true.bind
      }
      teamFilter && stateFilter
    })

  override def findNotesQuery(enquiryIDs: Set[UUID]): Query[EnquiryNotes, StoredEnquiryNote, Seq] =
    enquiryNotes.table.filter(_.enquiryID.inSet(enquiryIDs))

  override def searchQuery(q: EnquirySearchQuery): Query[Enquiries, StoredEnquiry, Seq] = {
    def queries(e: Enquiries, client: Clients, m: Rep[Option[Message.Messages]], f: Rep[Option[UploadedFileDao.UploadedFiles]], tm: Rep[Option[Members]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          (
            e.searchableKey @+
            e.searchableSubject @+
            m.map(_.searchableText).orEmptyTsVector @+
            client.searchableUniversityID @+
            client.searchableFullName @+
            f.map(_.searchableFileName).orEmptyTsVector @+
            tm.map(_.searchableUsercode).orEmptyTsVector @+
            tm.map(_.searchableFullName).orEmptyTsVector
          ).? @@ prefixTsQuery(queryStr.bind)
        },
        q.createdAfter.map { d => e.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => e.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.team.map { team => e.team.? === team },
        q.member.map { member => tm.map(_.usercode === member) },
        q.state.flatMap {
          case IssueStateFilter.All => None
          case IssueStateFilter.Open => Some(e.isOpen.?)
          case IssueStateFilter.Closed => Some(!e.isOpen.?)
        }
      ).flatten

    enquiries.table
      .withClientAndMessages
      .joinLeft(Owner.owners.table.join(MemberDao.members.table).on(_.userId === _.usercode))
      .on { case ((e, _, _), (o, _)) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry: Owner.EntityType) }
      .filter { case ((e, c, mf), o) => queries(e, c, mf.map(_._1), mf.flatMap(_._2), o.map { case (_, tm) => tm }).reduce(_ && _) }
      .map { case ((e, _, _), _) => (e, e.isOpen) }
      .sortBy { case (e, isOpen) => (isOpen.desc, e.created.desc) }
      .distinct
      .map { case (e, _) => e }
  }

  override def getLastUpdatedForClients(clients: Set[UniversityID]): DBIO[Seq[(UniversityID, Option[OffsetDateTime])]] = {
    enquiries.table
      .filter(_.universityId.inSet(clients))
      .joinLeft(Message.lastUpdatedEnquiryMessage)
      .on { case (e, (id, _)) => e.id === id }
      .map { case (e, messages) => (e.universityId, e.version, messages.flatMap(_._2)) }
      .groupBy { case (client, _, _) => client }
      .map { case (client, tuple) => (client, tuple.map(_._2).max, tuple.map(_._3).max) }
      .map { case (client, enquiryUpdated, m) =>
        // working out the most recent date is made easier if we deal with an arbitrary min date rather than handling the options
        val MinDate = OffsetDateTime.from(Instant.EPOCH.atOffset(ZoneOffset.UTC))

        val latestMessage = m.getOrElse(MinDate)

        val mostRecentUpdate = slick.lifted.Case.If(latestMessage > enquiryUpdated).Then(latestMessage.?).Else(enquiryUpdated)

        (client, mostRecentUpdate)
      }
      .result
  }

  override def findByStateQuery(state: IssueStateFilter): Query[Enquiries, StoredEnquiry, Seq] =
    enquiries.table.filter(_.matchesState(state))

  implicit val getUUIDResult = GetResult(r => UUID.fromString(r.nextString))

  override def getFirstEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Team): DBIO[Seq[StoredEnquiry]] =
    sql"""select e.id
      from enquiry e
      join (
        select team_id, university_id, min(created_utc) as firstdate
          from enquiry
          where team_id = ${team.id}
          and created_utc >= $start
          and created_utc < $end
          group by university_id, team_id
      ) f
      on (
        e.team_id = f.team_id and
          e.university_id = f.university_id and
          e.created_utc = f.firstdate
      )
      order by e.id;""".as[UUID]
      .flatMap(idSeq => {
      enquiries.table
        .filter(_.id.inSet(idSeq))
        .result
    })

  override def countFirstEnquiriesByTeam(start: OffsetDateTime, end: OffsetDateTime): DBIO[Seq[TeamMetrics]] =
    sql"""select e.team_id, count(e.id)
      from enquiry e
      join (
        select team_id, university_id, min(created_utc) as firstdate
          from enquiry
          where created_utc >= $start
          and created_utc < $end
          group by university_id, team_id
      ) f
      on (
        e.team_id = f.team_id and
        e.university_id = f.university_id and
        e.created_utc = f.firstdate
      )
      group by e.team_id;""".as[(String, Int)]
      .map(_.map { case (id, value) =>
        TeamMetrics(Teams.fromId(id), value)
      })

  override def getEnquiryHistory(id: UUID): DBIO[Seq[StoredEnquiryVersion]] =
    enquiries.history(_.id === id)
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
    state: IssueState,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    created: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredEnquiry] with Created with Teamable {
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
      id = id,
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
    version: OffsetDateTime = JavaTime.offsetDateTime,
    created: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredEnquiry] with Created

  sealed trait CommonEnquiryProperties {
    self: Table[_] =>

    def key = column[IssueKey]("enquiry_key")
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def universityId = column[UniversityID]("university_id")
    def subject = column[String]("subject")
    def state = column[IssueState]("state")
    def created = column[OffsetDateTime]("created_utc")
  }

  class Enquiries(tag: Tag) extends Table[StoredEnquiry](tag, "enquiry") with VersionedTable[StoredEnquiry] with CommonEnquiryProperties {
    override def matchesPrimaryKey(other: StoredEnquiry): Rep[Boolean] = id === other.id

    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
    def searchableKey: Rep[TsVector] = column[TsVector]("enquiry_key_tsv")
    def searchableSubject: Rep[TsVector] = column[TsVector]("subject_tsv")

    def * = (id, key, universityId, subject, team, state, version, created).mapTo[StoredEnquiry]
    def idx = index("idx_enquiry_key", key, unique = true)

    def isOpen: Rep[Boolean] = state === (Open : IssueState) || state === (Reopened : IssueState)

    def matchesState(state: IssueStateFilter): Rep[Boolean] = state match {
      case IssueStateFilter.Open => isOpen
      case IssueStateFilter.Closed => !isOpen
      case IssueStateFilter.All => true.bind
    }
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

    def withClientAndMessages = q
      .withClient
      .joinLeft(
        Message.messages.table
          .withUploadedFiles
          .joinLeft(MemberDao.members.table)
          .on { case ((m, _), member) => m.teamMember.map(_ === member.usercode) }
          .map { case ((m, f), member) => (m, f, member) }
      )
      .on { case ((e, _), (m, _, _)) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
      .map { case ((e, c), mfm) => (e, c, mfm) }

    def withLastUpdatedFor(usercode: Usercode) = q
      .withClient
      .join(Message.lastUpdatedEnquiryMessage)
      .on { case ((e, _), (id, _)) => e.id === id }
      .map { case ((e, c), (_, lastMessage)) => (e, c, lastMessage) }
      .join(Message.lastUpdatedEnquiryMessageFromClient)
      .on { case ((e, _, _), (id, _)) => e.id === id }
      .map { case ((e, c, lastMessage), (_, lastMessageFromClient)) => (e, c, lastMessage, lastMessageFromClient) }
      .joinLeft(AuditEvent.latestEventsForUser(Operation.Enquiry.View, usercode, Target.Enquiry))
      .on { case ((e, _, _, _), (targetId, _)) => e.id.asColumnOf[String] === targetId }
      .map { case ((e, c, lastMessage, lastMessageFromClient), o) => (e, c, lastMessage, lastMessageFromClient, o.flatMap(_._2)) }
      .map { case (e, c, lastMessage, lastMessageFromClient, lastViewed) =>
        val MinDate = OffsetDateTime.from(Instant.EPOCH.atOffset(ZoneOffset.UTC))
        val lastModified = lastMessage.getOrElse(MinDate)
        val mostRecentUpdate =
          slick.lifted.Case.If(lastModified > e.version)
            .Then(lastModified)
            .Else(e.version)

        // Only consider the last message from client if it's the most recent message in general
        val latestMessageFromClient = lastMessageFromClient.getOrElse(MinDate)
        val mostRecentMessageFromClient: Rep[Option[OffsetDateTime]] =
          slick.lifted.Case.If(lastModified > latestMessageFromClient)
            .Then(Option.empty[OffsetDateTime])
            .Else(lastMessageFromClient)

        (e, c, mostRecentUpdate, mostRecentMessageFromClient, lastViewed)
      }
  }

  case class StoredEnquiryNote(
    id: UUID,
    enquiryID: UUID,
    noteType: EnquiryNoteType,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredEnquiryNote] with Created {
    def asEnquiryNote(member: Member) = EnquiryNote(
      id,
      noteType,
      text,
      member,
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
  ) extends StoredVersion[StoredEnquiryNote] with Created

  trait CommonNoteProperties { self: Table[_] =>
    def enquiryID = column[UUID]("enquiry_id")
    def noteType = column[EnquiryNoteType]("note_type")
    def text = column[String]("text")
    def teamMember = column[Usercode]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class EnquiryNotes(tag: Tag) extends Table[StoredEnquiryNote](tag, "enquiry_note")
    with VersionedTable[StoredEnquiryNote]
    with CommonNoteProperties {
    override def matchesPrimaryKey(other: StoredEnquiryNote): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)
    def searchableText: Rep[TsVector] = column[TsVector]("text_tsv")

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

  implicit class EnquiryNoteExtensions[C[_]](q: Query[EnquiryNotes, StoredEnquiryNote, C]) {
    def withMember = q
      .join(MemberDao.members.table)
      .on(_.teamMember === _.usercode)
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

  case class EnquiryFilter(
    team: Option[Team] = None,
    owner: Set[Usercode] = Set.empty,
  ) {
    require(team.nonEmpty || owner.nonEmpty, "One of team or owner must be set")

    def withOwners(owners: Set[Usercode]): EnquiryFilter = copy(owner = owners)
  }

  object EnquiryFilter {
    def apply(team: Team): EnquiryFilter = EnquiryFilter(team = Some(team))
    def apply(owner: Usercode): EnquiryFilter = EnquiryFilter(owner = Set(owner))
    def apply(team: Team, owner: Usercode): EnquiryFilter = EnquiryFilter(team = Some(team), owner = Set(owner))
    def none: EnquiryFilter = EnquiryFilter(team = None)
  }
}
