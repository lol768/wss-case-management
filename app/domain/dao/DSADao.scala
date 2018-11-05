package domain.dao

import java.time._
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._

import domain._
import domain.dao.CaseDao._

import domain.dao.DSADao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey}
import slick.lifted.ProvenShape
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[DSADaoImpl])
trait DSADao {
  def insert(dsaApplication: DSAApplication, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[DSAApplication]
  def update(dsaApplication: DSAApplication, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[DSAApplication]
  def delete(dsaApplication: DSAApplication)(implicit ac: AuditLogContext): DBIO[Done]
  def findDSAApplication(id: UUID): DBIO[DSAApplication]
  def getDSAHistory(caseID: UUID): DBIO[Seq[DSAApplicationVersion]]
  def insertFundingTypes(tags: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Seq[StoredDSAFundingType]]
  def deleteFundingTypes(tags: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Done]
  def findFundingTypesQuery(dsaApplicationIds: Set[UUID]): Query[DSAFundingTypes, StoredDSAFundingType, Seq]
  def getDSAFundingTypeHistory(caseID: UUID): DBIO[Seq[StoredDSAFundingTypeVersion]]
}

@Singleton
class DSADaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends DSADao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(dsaApplication: DSAApplication, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[DSAApplication] = {
    dsaApplications.insert(dsaApplication.copy(version = version))
  }

  override def update(dsaApplication: DSAApplication, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[DSAApplication] = {
    dsaApplications.update(dsaApplication.copy(version = version))
  }

  override def delete(dsaApplication: DSAApplication)(implicit ac: AuditLogContext): DBIO[Done] = {
    dsaApplications.delete(dsaApplication)
  }

  override def findDSAApplication(id: UUID): DBIO[DSAApplication] = {
    dsaApplications.table.filter(_.id === id).result.head
  }

  override def getDSAHistory(caseID: UUID): DBIO[Seq[DSAApplicationVersion]] = {
    val dsaIds = cases.versionsTable.filter(c => c.id === caseID).map(_.dsaApplication)
    dsaApplications.versionsTable.filter(_.id.? in dsaIds).sortBy(_.timestamp).result
  }

  override def insertFundingTypes(types: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Seq[StoredDSAFundingType]] =
    dsaFundingTypes.insertAll(types.toSeq)

  override def deleteFundingTypes(types: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Done] =
    dsaFundingTypes.deleteAll(types.toSeq)

  override def findFundingTypesQuery(dsaApplicationIds: Set[UUID]): Query[DSAFundingTypes, StoredDSAFundingType, Seq] =
    dsaFundingTypes.table.filter(_.dsaApplicationID.inSet(dsaApplicationIds))

  override def getDSAFundingTypeHistory(caseID: UUID): DBIO[Seq[StoredDSAFundingTypeVersion]] = {
    val dsaIds = cases.versionsTable.filter(c => c.id === caseID).map(_.dsaApplication)
    dsaFundingTypes.versionsTable.filter(_.dsaApplicationID.? in dsaIds).sortBy(_.timestamp).result
  }

}

object DSADao {

  val dsaApplications: VersionedTableQuery[DSAApplication, DSAApplicationVersion, DSAApplications, DSAApplicationVersions] =
    VersionedTableQuery(TableQuery[DSAApplications], TableQuery[DSAApplicationVersions])

  val dsaFundingTypes: VersionedTableQuery[StoredDSAFundingType, StoredDSAFundingTypeVersion, DSAFundingTypes, DSAFundingTypeVersions] =
    VersionedTableQuery(TableQuery[DSAFundingTypes], TableQuery[DSAFundingTypeVersions])

  case class DSAApplication (
    id: Option[UUID],
    applicationDate: Option[OffsetDateTime],
    fundingApproved: Option[Boolean],
    confirmationDate: Option[OffsetDateTime],
    ineligibilityReason: Option[DSAIneligibilityReason],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[DSAApplication] {

    override def atVersion(at: OffsetDateTime): DSAApplication = copy(version = at)

    override def storedVersion[B <: StoredVersion[DSAApplication]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      DSAApplicationVersion(
        id.get,
        applicationDate,
        fundingApproved,
        confirmationDate,
        ineligibilityReason,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class DSAApplicationVersion(
    id: UUID,
    applicationDate: Option[OffsetDateTime],
    fundingApproved: Option[Boolean],
    confirmationDate: Option[OffsetDateTime],
    ineligibilityReason: Option[DSAIneligibilityReason],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[DSAApplication] {

    def asApplication = DSAApplication(
      Some(id),
      applicationDate,
      fundingApproved,
      confirmationDate,
      ineligibilityReason,
      version,
    )
  }

  trait CommonDSAApplicationProperties { self: Table[_] =>
    def applicationDate = column[Option[OffsetDateTime]]("application_date_utc")
    def fundingApproved = column[Option[Boolean]]("funding_approved")
    def confirmationDate = column[Option[OffsetDateTime]]("confirmation_date_utc")
    def ineligibilityReason = column[Option[DSAIneligibilityReason]]("ineligibility_reason")
    def version = column[OffsetDateTime]("version_utc")
  }

  class DSAApplications(tag: Tag) extends Table[DSAApplication](tag, "dsa_application")
    with VersionedTable[DSAApplication]
    with CommonDSAApplicationProperties {

    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

    override def matchesPrimaryKey(other: DSAApplication): Rep[Boolean] = id === other.id.orNull

    override def * : ProvenShape[DSAApplication] =
      (id.?, applicationDate, fundingApproved, confirmationDate, ineligibilityReason, version).mapTo[DSAApplication]

    def fk = foreignKey("fk_dsa_application", id, cases.table)(_.id)
  }

  class DSAApplicationVersions(tag: Tag) extends Table[DSAApplicationVersion](tag, "dsa_application_version")
    with StoredVersionTable[DSAApplication]
    with CommonDSAApplicationProperties {

    def id: Rep[UUID] = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[DSAApplicationVersion] = (id, applicationDate, fundingApproved, confirmationDate, ineligibilityReason, version, operation, timestamp, auditUser).mapTo[DSAApplicationVersion]

    def pk: PrimaryKey = primaryKey("pk_version_dsa_application", (id, timestamp))
    def idx: Index = index("idx_dsa_application_version", (id, version))
  }

  case class StoredDSAFundingType(
    dsaApplicationID: UUID,
    fundingType: DSAFundingType,
    version: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredDSAFundingType] {
    override def atVersion(at: OffsetDateTime): StoredDSAFundingType = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredDSAFundingType]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredDSAFundingTypeVersion(
        dsaApplicationID,
        fundingType,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredDSAFundingTypeVersion(
    dsaApplicationID: UUID,
    fundingType: DSAFundingType,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredDSAFundingType]

  trait CommonFundingTypeProperties { self: Table[_] =>
    def dsaApplicationID = column[UUID]("dsa_application_id")
    def fundingType = column[DSAFundingType]("funding_type")
    def version = column[OffsetDateTime]("version_utc")
  }

  class DSAFundingTypes(tag: Tag) extends Table[StoredDSAFundingType](tag, "dsa_funding_type")
    with VersionedTable[StoredDSAFundingType]
    with CommonFundingTypeProperties {
    override def matchesPrimaryKey(other: StoredDSAFundingType): Rep[Boolean] =
      dsaApplicationID === other.dsaApplicationID && fundingType === other.fundingType

    override def * : ProvenShape[StoredDSAFundingType] = (dsaApplicationID, fundingType, version).mapTo[StoredDSAFundingType]
    def pk = primaryKey("pk_dsa_funding_type", (dsaApplicationID, fundingType))
    def fk = foreignKey("fk_dsa_funding_type", dsaApplicationID, dsaApplications.table)(_.id)
    def idx = index("idx_dsa_funding_type", dsaApplicationID)
  }

  class DSAFundingTypeVersions(tag: Tag) extends Table[StoredDSAFundingTypeVersion](tag, "dsa_funding_type_version")
    with StoredVersionTable[StoredDSAFundingType]
    with CommonFundingTypeProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredDSAFundingTypeVersion] =
      (dsaApplicationID, fundingType, version, operation, timestamp, auditUser).mapTo[StoredDSAFundingTypeVersion]
    def pk = primaryKey("pk_dsa_funding_type_version", (dsaApplicationID, fundingType, timestamp))
    def idx = index("idx_dsa_funding_type_version", (dsaApplicationID, fundingType, version))
  }
}