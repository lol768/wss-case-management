package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import enumeratum.{EnumEntry, PlayEnum}
import ExtendedPostgresProfile.api._
import warwick.sso.Usercode
import scala.collection.immutable
import scala.language.higherKinds

case class Owner(
  entityId: UUID,
  entityType: Owner.EntityType,
  userId: Usercode,
  version: OffsetDateTime
) extends Versioned[Owner] {

  override def atVersion(at: OffsetDateTime): Owner = copy(version = at)
  override def storedVersion[B <: StoredVersion[Owner]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
    OwnerVersion(
      entityId,
      entityType,
      userId,
      version,
      operation,
      timestamp
    ).asInstanceOf[B]

}

object EnquiryOwner {
  def apply(enquiryId: UUID, userId: Usercode, version: OffsetDateTime = OffsetDateTime.now()) =
    Owner(entityId = enquiryId, entityType = Owner.EntityType.Enquiry, userId = userId, version = version)
}

object CaseOwner {
  def apply(caseId: UUID, userId: Usercode, version: OffsetDateTime = OffsetDateTime.now()) =
    Owner(entityId = caseId, entityType = Owner.EntityType.Case, userId = userId, version = version)
}

object Owner extends Versioning {

  sealed trait EntityType extends EnumEntry
  object EntityType extends PlayEnum[EntityType] {
    case object Enquiry extends EntityType
    case object Case extends EntityType

    val values: immutable.IndexedSeq[EntityType] = findValues
  }

  def tupled = (Owner.apply _).tupled

  sealed trait OwnerProperties {
    self: Table[_] =>

    def entityId = column[UUID]("entity_id")
    def entityType = column[Owner.EntityType]("entity_type")
    def userId = column[Usercode]("user_id")
    def version = column[OffsetDateTime]("version_utc")

  }

  class Owners(tag: Tag) extends Table[Owner](tag, "owner") with VersionedTable[Owner] with OwnerProperties {
    override def matchesPrimaryKey(other: Owner): Rep[Boolean] = entityId === other.entityId && entityType === other.entityType && userId === other.userId

    def pk = primaryKey("pk_owner", (entityId, entityType, userId))

    def * = (entityId, entityType, userId, version).mapTo[Owner]
  }

  class OwnerVersions(tag: Tag) extends Table[OwnerVersion](tag, "owner_version") with StoredVersionTable[Owner] with OwnerProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    def * = (entityId, entityType, userId, version, operation, timestamp).mapTo[OwnerVersion]
    def pk = primaryKey("pk_ownerversions", (entityId, entityType, userId, timestamp))
    def idx = index("idx_ownerversions", (entityId, entityType, userId, version))
  }

  val owners: VersionedTableQuery[Owner, OwnerVersion, Owners, OwnerVersions] =
    VersionedTableQuery(TableQuery[Owners], TableQuery[OwnerVersions])

}

case class OwnerVersion(
  entityId: UUID,
  entityType: Owner.EntityType,
  userId: Usercode,
  version: OffsetDateTime = OffsetDateTime.now(),
  operation: DatabaseOperation,
  timestamp: OffsetDateTime
) extends StoredVersion[Owner]
