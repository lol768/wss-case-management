package domain.dao

import java.time._
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.CaseDao._
import domain.dao.DSADao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[DSADaoImpl])
trait DSADao {
  def insert(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[StoredDSAApplication]
  def update(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[StoredDSAApplication]
  def delete(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[Done]
  def findDSAApplication(id: UUID): DBIO[StoredDSAApplication]
  def getDSAHistory(caseID: UUID): DBIO[Seq[StoredDSAApplicationVersion]]
  def insertFundingTypes(tags: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Seq[StoredDSAFundingType]]
  def deleteFundingTypes(tags: Set[StoredDSAFundingType])(implicit ac: AuditLogContext): DBIO[Done]
  def findFundingTypesQuery(dsaApplicationIds: Set[UUID]): Query[DSAFundingTypes, StoredDSAFundingType, Seq]
  def getDSAFundingTypeHistory(caseID: UUID): DBIO[Seq[StoredDSAFundingTypeVersion]]
}

@Singleton
class DSADaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends DSADao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  override def insert(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[StoredDSAApplication] = {
    dsaApplications.insert(dsaApplication)
  }

  override def update(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[StoredDSAApplication] = {
    dsaApplications.update(dsaApplication)
  }

  override def delete(dsaApplication: StoredDSAApplication)(implicit ac: AuditLogContext): DBIO[Done] = {
    dsaApplications.delete(dsaApplication)
  }

  override def findDSAApplication(id: UUID): DBIO[StoredDSAApplication] = {
    dsaApplications.table.filter(_.id === id).result.head
  }

  override def getDSAHistory(caseID: UUID): DBIO[Seq[StoredDSAApplicationVersion]] = {
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

  import CustomJdbcTypes._

  val dsaApplications: VersionedTableQuery[StoredDSAApplication, StoredDSAApplicationVersion, DSAApplications, DSAApplicationVersions] =
    VersionedTableQuery(TableQuery[DSAApplications], TableQuery[DSAApplicationVersions])

  val dsaFundingTypes: VersionedTableQuery[StoredDSAFundingType, StoredDSAFundingTypeVersion, DSAFundingTypes, DSAFundingTypeVersions] =
    VersionedTableQuery(TableQuery[DSAFundingTypes], TableQuery[DSAFundingTypeVersions])

  case class StoredDSAApplication (
    id: UUID,
    customerReference: Option[String],
    applicationDate: Option[LocalDate],
    fundingApproved: Option[Boolean],
    confirmationDate: Option[LocalDate],
    ineligibilityReason: Option[DSAIneligibilityReason],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredDSAApplication] {

    override def atVersion(at: OffsetDateTime): StoredDSAApplication = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredDSAApplication]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredDSAApplicationVersion(
        id,
        customerReference,
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

  case class StoredDSAApplicationVersion(
    id: UUID,
    customerReference: Option[String],
    applicationDate: Option[LocalDate],
    fundingApproved: Option[Boolean],
    confirmationDate: Option[LocalDate],
    ineligibilityReason: Option[DSAIneligibilityReason],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredDSAApplication] {

    def asApplication = StoredDSAApplication(
      id,
      customerReference,
      applicationDate,
      fundingApproved,
      confirmationDate,
      ineligibilityReason,
      version,
    )
  }

  trait CommonDSAApplicationProperties { self: Table[_] =>
    def customerReference = column[Option[String]]("customer_reference")
    def applicationDate = column[Option[LocalDate]]("application_date_utc")
    def fundingApproved = column[Option[Boolean]]("funding_approved")
    def confirmationDate = column[Option[LocalDate]]("confirmation_date_utc")
    def ineligibilityReason = column[Option[DSAIneligibilityReason]]("ineligibility_reason")
    def version = column[OffsetDateTime]("version_utc")
  }

  class DSAApplications(tag: Tag) extends Table[StoredDSAApplication](tag, "dsa_application")
    with VersionedTable[StoredDSAApplication]
    with CommonDSAApplicationProperties {

    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

    override def matchesPrimaryKey(other: StoredDSAApplication): Rep[Boolean] = id === other.id

    override def * : ProvenShape[StoredDSAApplication] =
      (id, customerReference, applicationDate, fundingApproved, confirmationDate, ineligibilityReason, version).mapTo[StoredDSAApplication]

    def fk = foreignKey("fk_dsa_application", id, cases.table)(_.id)
  }

  class DSAApplicationVersions(tag: Tag) extends Table[StoredDSAApplicationVersion](tag, "dsa_application_version")
    with StoredVersionTable[StoredDSAApplication]
    with CommonDSAApplicationProperties {

    def id: Rep[UUID] = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredDSAApplicationVersion] = (id, customerReference, applicationDate, fundingApproved, confirmationDate, ineligibilityReason, version, operation, timestamp, auditUser).mapTo[StoredDSAApplicationVersion]

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
