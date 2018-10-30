package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import enumeratum.{EnumEntry, PlayEnum}
import ExtendedPostgresProfile.api._
import domain.dao.MemberDao
import helpers.JavaTime
import services.AuditLogContext
import warwick.sso.Usercode

import scala.collection.immutable
import scala.language.higherKinds

case class Owner(
  entityId: UUID,
  entityType: Owner.EntityType,
  userId: Usercode,
  outlookId: Option[String],
  version: OffsetDateTime
) extends Versioned[Owner] {

  override def atVersion(at: OffsetDateTime): Owner = copy(version = at)
  override def storedVersion[B <: StoredVersion[Owner]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
    OwnerVersion(
      entityId,
      entityType,
      userId,
      outlookId,
      version,
      operation,
      timestamp,
      ac.usercode
    ).asInstanceOf[B]

}

object EnquiryOwner {
  def apply(enquiryId: UUID, userId: Usercode, version: OffsetDateTime = JavaTime.offsetDateTime) =
    Owner(entityId = enquiryId, entityType = Owner.EntityType.Enquiry, userId = userId, version = version, outlookId = None)
}

object CaseOwner {
  def apply(caseId: UUID, userId: Usercode, version: OffsetDateTime = JavaTime.offsetDateTime) =
    Owner(entityId = caseId, entityType = Owner.EntityType.Case, userId = userId, version = version, outlookId = None)
}

object AppointmentOwner {
  def apply(appointmentID: UUID, userId: Usercode, version: OffsetDateTime = JavaTime.offsetDateTime) =
    Owner(entityId = appointmentID, entityType = Owner.EntityType.Appointment, userId = userId, version = version, outlookId = None)
}

object Owner extends Versioning {

  sealed trait EntityType extends EnumEntry
  object EntityType extends PlayEnum[EntityType] {
    case object Enquiry extends EntityType
    case object Case extends EntityType
    case object Appointment extends EntityType

    val values: immutable.IndexedSeq[EntityType] = findValues
  }

  def tupled = (Owner.apply _).tupled

  sealed trait OwnerProperties {
    self: Table[_] =>

    def entityId = column[UUID]("entity_id")
    def entityType = column[Owner.EntityType]("entity_type")
    def userId = column[Usercode]("user_id")
    def version = column[OffsetDateTime]("version_utc")
    def outlookId = column[Option[String]]("outlook_id")

  }

  class Owners(tag: Tag) extends Table[Owner](tag, "owner") with VersionedTable[Owner] with OwnerProperties {
    override def matchesPrimaryKey(other: Owner): Rep[Boolean] = entityId === other.entityId && entityType === other.entityType && userId === other.userId

    def pk = primaryKey("pk_owner", (entityId, entityType, userId))

    def * = (entityId, entityType, userId, outlookId, version).mapTo[Owner]
  }

  class OwnerVersions(tag: Tag) extends Table[OwnerVersion](tag, "owner_version") with StoredVersionTable[Owner] with OwnerProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (entityId, entityType, userId, outlookId, version, operation, timestamp, auditUser).mapTo[OwnerVersion]
    def pk = primaryKey("pk_ownerversions", (entityId, entityType, userId, timestamp))
    def idx = index("idx_ownerversions", (entityId, entityType, userId, version))
  }

  implicit class OwnerExtensions[C[_]](val q: Query[Owners, Owner, C]) extends AnyVal {
    def withMember = q
      .join(MemberDao.members.table)
      .on { case (o, m) => o.userId === m.usercode }
  }

  val owners: VersionedTableQuery[Owner, OwnerVersion, Owners, OwnerVersions] =
    VersionedTableQuery(TableQuery[Owners], TableQuery[OwnerVersions])

}

case class OwnerVersion(
  entityId: UUID,
  entityType: Owner.EntityType,
  userId: Usercode,
  outlookId: Option[String],
  version: OffsetDateTime = JavaTime.offsetDateTime,
  operation: DatabaseOperation,
  timestamp: OffsetDateTime,
  auditUser: Option[Usercode]
) extends StoredVersion[Owner]
