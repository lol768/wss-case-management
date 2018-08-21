package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.UniversityID
import slick.jdbc.PostgresProfile.api._
import CustomJdbcTypes._
import domain.EnquiryState.{Open, Reopened}
import enumeratum.{EnumEntry, PlayEnum}

import scala.language.higherKinds

case class Enquiry(
  id: Option[UUID] = None,
  universityID: UniversityID,
  team: Team,
  state: EnquiryState = Open,
  version: OffsetDateTime = OffsetDateTime.now(),
) extends Versioned[Enquiry] {

  override def atVersion(at: OffsetDateTime): Enquiry = copy(version = at)
  override def storedVersion[B <: StoredVersion[Enquiry]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
    EnquiryVersion(
      id.get,
      universityID,
      team,
      state,
      version,
      operation,
      timestamp
    ).asInstanceOf[B]

}

object Enquiry extends Versioning {
  def tupled = (Enquiry.apply _).tupled

  case class FormData(
    text: String
  )

  sealed trait EnquiryProperties {
    self: Table[_] =>

    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version")
    def universityId = column[UniversityID]("university_id")
    def state = column[EnquiryState]("state")
  }

  class Enquiries(tag: Tag) extends Table[Enquiry](tag, "enquiry") with VersionedTable[Enquiry] with EnquiryProperties {
    override def matchesPrimaryKey(other: Enquiry): Rep[Boolean] = id === other.id.orNull

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id.?, universityId, team, state, version).mapTo[Enquiry]
  }

  implicit class EnquiriesQueryOps(enquiries: Enquiries){
    def isOpen = enquiries.state === (Open : EnquiryState) || enquiries.state === (Reopened : EnquiryState)
  }

  class EnquiryVersions(tag: Tag) extends Table[EnquiryVersion](tag, "enquiry_version") with StoredVersionTable[Enquiry] with EnquiryProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp")

    def * = (id, universityId, team, state, version, operation, timestamp).mapTo[EnquiryVersion]
    def pk = primaryKey("pk_enquiryversions", (id, timestamp))
    def idx = index("idx_enquiryversions", (id, version))
  }

  val enquiries: VersionedTableQuery[Enquiry, EnquiryVersion, Enquiries, EnquiryVersions] =
    VersionedTableQuery(TableQuery[Enquiries], TableQuery[EnquiryVersions])

  implicit class EnquiryExtensions[C[_]](q: Query[Enquiries, Enquiry, C]) {
    def withMessages = q
      .joinLeft(Message.messages.table)
      .on { (e, m) =>
        e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
      }
  }
}

case class EnquiryVersion(
  id: UUID,
  universityID: UniversityID,
  team: Team,
  state: EnquiryState,
  version: OffsetDateTime = OffsetDateTime.now(),
  operation: DatabaseOperation,
  timestamp: OffsetDateTime
) extends StoredVersion[Enquiry]

sealed trait EnquiryState extends EnumEntry
object EnquiryState extends PlayEnum[EnquiryState] {
  case object Open extends EnquiryState
  case object Closed extends EnquiryState
  case object Reopened extends EnquiryState

  val values = findValues
}

