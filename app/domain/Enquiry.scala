package domain

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.IssueState.{Open, Reopened}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import helpers.StringUtils._
import play.api.data.Form
import play.api.data.Forms._
import services.AuditLogContext
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

import scala.collection.immutable
import scala.language.higherKinds

case class Enquiry(
  id: Option[UUID] = None,
  key: Option[IssueKey] = None,
  universityID: UniversityID,
  subject: String,
  team: Team,
  state: IssueState = Open,
  version: OffsetDateTime = OffsetDateTime.now(),
  created: OffsetDateTime = OffsetDateTime.now(),
) extends Versioned[Enquiry] {

  override def atVersion(at: OffsetDateTime): Enquiry = copy(version = at)
  override def storedVersion[B <: StoredVersion[Enquiry]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
    EnquiryVersion(
      id.get,
      key.get,
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

}

object Enquiry extends Versioning {
  def tupled = (Enquiry.apply _).tupled

  val SubjectMaxLength = 200

  case class FormData(
    subject: String,
    text: String
  )

  val form = Form(mapping(
    "subject" -> nonEmptyText(maxLength = Enquiry.SubjectMaxLength),
    "text" -> nonEmptyText
  )(FormData.apply)(FormData.unapply))

  sealed trait EnquiryProperties {
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

  class Enquiries(tag: Tag) extends Table[Enquiry](tag, "enquiry") with VersionedTable[Enquiry] with EnquiryProperties {
    override def matchesPrimaryKey(other: Enquiry): Rep[Boolean] = id === other.id.orNull

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id.?, key.?, universityId, subject, team, state, version, created).mapTo[Enquiry]
    def idx = index("idx_enquiry_key", key, unique = true)

    def isOpen = state === (Open : IssueState) || state === (Reopened : IssueState)
  }

  class EnquiryVersions(tag: Tag) extends Table[EnquiryVersion](tag, "enquiry_version") with StoredVersionTable[Enquiry] with EnquiryProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (id, key, universityId, subject, team, state, version, created, operation, timestamp, auditUser).mapTo[EnquiryVersion]
    def pk = primaryKey("pk_enquiryversions", (id, timestamp))
    def idx = index("idx_enquiryversions", (id, version))
  }

  val enquiries: VersionedTableQuery[Enquiry, EnquiryVersion, Enquiries, EnquiryVersions] =
    VersionedTableQuery(TableQuery[Enquiries], TableQuery[EnquiryVersions])

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

  val enquiryNotes: VersionedTableQuery[StoredEnquiryNote, StoredEnquiryNoteVersion, EnquiryNotes, EnquiryNoteVersions] =
    VersionedTableQuery(TableQuery[EnquiryNotes], TableQuery[EnquiryNoteVersions])

  implicit class EnquiryExtensions[C[_]](q: Query[Enquiries, Enquiry, C]) {
    def withMessages = q
      .joinLeft(Message.messages.table.withUploadedFiles)
      .on { case (e, (m, _)) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
    def withNotes = q
      .joinLeft(enquiryNotes.table)
      .on { case (e, n) => e.id === n.enquiryID }
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

case class EnquiryRender(
  enquiry: Enquiry,
  messages: Seq[MessageRender],
  notes: Seq[EnquiryNote]
)

case class EnquiryVersion(
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
) extends StoredVersion[Enquiry]

case class EnquiryNote(
  id: UUID,
  noteType: EnquiryNoteType,
  text: String,
  teamMember: Usercode,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

object EnquiryNote {
  // oldest first
  val dateOrdering: Ordering[EnquiryNote] = Ordering.by[EnquiryNote, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

/**
  * Just the data of a enquiry note required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class EnquiryNoteSave(
  text: String,
  teamMember: Usercode
)

sealed abstract class EnquiryNoteType(val description: String) extends EnumEntry
object EnquiryNoteType extends PlayEnum[EnquiryNoteType] {
  case object Referral extends EnquiryNoteType("Referral")

  override def values: immutable.IndexedSeq[EnquiryNoteType] = findValues
}


