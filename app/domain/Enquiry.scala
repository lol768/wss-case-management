package domain

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.IssueState.{Open, Reopened}
import helpers.StringUtils._
import play.api.data.Form
import play.api.data.Forms._
import warwick.sso.UniversityID

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
  override def storedVersion[B <: StoredVersion[Enquiry]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
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
      timestamp
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

    def * = (id, key, universityId, subject, team, state, version, created, operation, timestamp).mapTo[EnquiryVersion]
    def pk = primaryKey("pk_enquiryversions", (id, timestamp))
    def idx = index("idx_enquiryversions", (id, version))
  }

  val enquiries: VersionedTableQuery[Enquiry, EnquiryVersion, Enquiries, EnquiryVersions] =
    VersionedTableQuery(TableQuery[Enquiries], TableQuery[EnquiryVersions])

  implicit class EnquiryExtensions[C[_]](q: Query[Enquiries, Enquiry, C]) {
    def withMessages = q
      .joinLeft(Message.messages.table.withUploadedFiles)
      .on { case (e, (m, _)) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
  }

  case class EnquirySearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    team: Option[Team] = None,
    state: Option[IssueStateFilter] = None
  ) {
    def isEmpty: Boolean = !nonEmpty
    def nonEmpty: Boolean =
      query.exists(_.hasText) ||
      createdAfter.nonEmpty ||
      createdBefore.nonEmpty ||
      team.nonEmpty ||
      state.nonEmpty
  }
}

case class EnquiryRender(
  enquiry: Enquiry,
  messages: Seq[(MessageData, Seq[UploadedFile])]
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
  timestamp: OffsetDateTime
) extends StoredVersion[Enquiry]




