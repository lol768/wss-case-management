package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID

import scala.language.higherKinds

case class EnquiryOwner(
  enquiryId: UUID,
  universityID: UniversityID,
  version: OffsetDateTime = OffsetDateTime.now()
) extends Versioned[EnquiryOwner] {

  override def atVersion(at: OffsetDateTime): EnquiryOwner = copy(version = at)
  override def storedVersion[B <: StoredVersion[EnquiryOwner]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
    EnquiryOwnerVersion(
      enquiryId,
      universityID,
      version,
      operation,
      timestamp
    ).asInstanceOf[B]

}

object EnquiryOwner extends Versioning {
  def tupled = (EnquiryOwner.apply _).tupled

  sealed trait EnquiryOwnerProperties {
    self: Table[_] =>

    def enquiryId = column[UUID]("enquiry_id")
    def universityId = column[UniversityID]("university_id")
    def version = column[OffsetDateTime]("version_utc")

  }

  class EnquiryOwners(tag: Tag) extends Table[EnquiryOwner](tag, "enquiry_owner") with VersionedTable[EnquiryOwner] with EnquiryOwnerProperties {
    override def matchesPrimaryKey(other: EnquiryOwner): Rep[Boolean] = enquiryId === other.enquiryId && universityId === other.universityID

    def pk = primaryKey("pk_enquiryowner", (enquiryId, universityId))

    def * = (enquiryId, universityId, version).mapTo[EnquiryOwner]
  }

  class EnquiryOwnerVersions(tag: Tag) extends Table[EnquiryOwnerVersion](tag, "enquiry_owner_version") with StoredVersionTable[EnquiryOwner] with EnquiryOwnerProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    def * = (enquiryId, universityId, version, operation, timestamp).mapTo[EnquiryOwnerVersion]
    def pk = primaryKey("pk_enquiryownerversions", (enquiryId, universityId, timestamp))
    def idx = index("idx_enquiryownerversions", (enquiryId, universityId, version))
  }

  val enquiryOwners: VersionedTableQuery[EnquiryOwner, EnquiryOwnerVersion, EnquiryOwners, EnquiryOwnerVersions] =
    VersionedTableQuery(TableQuery[EnquiryOwners], TableQuery[EnquiryOwnerVersions])

}

case class EnquiryOwnerVersion(
  enquiryId: UUID,
  universityID: UniversityID,
  version: OffsetDateTime = OffsetDateTime.now(),
  operation: DatabaseOperation,
  timestamp: OffsetDateTime
) extends StoredVersion[EnquiryOwner]
