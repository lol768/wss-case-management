package domain.dao

import java.time.OffsetDateTime

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.ClientDao.StoredClient
import domain.dao.ClientSummaryDao.{StoredClientSummary, StoredClientSummaryVersion}
import domain.dao.ClientSummaryDao.StoredClientSummary.{ClientSummaries, ReasonableAdjustments, StoredReasonableAdjustment}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[ClientSummaryDaoImpl])
trait ClientSummaryDao {
  def insert(summary: StoredClientSummary)(implicit ac: AuditLogContext): DBIO[StoredClientSummary]
  def update(summary: StoredClientSummary, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClientSummary]
  def insertReasonableAdjustments(reasonableAdjustments: Set[StoredReasonableAdjustment])(implicit ac: AuditLogContext): DBIO[Seq[StoredReasonableAdjustment]]
  def deleteReasonableAdjustments(reasonableAdjustments: Set[StoredReasonableAdjustment])(implicit ac: AuditLogContext): DBIO[Done]
  def get(universityID: UniversityID): DBIO[Option[(StoredClientSummary, StoredClient)]]
  def getByAlternativeEmailAddress(email: String): DBIO[Option[(StoredClientSummary, StoredClient)]]
  def getReasonableAdjustmentsQuery(universityID: UniversityID): Query[ReasonableAdjustments, StoredReasonableAdjustment, Seq]
  def getHistory(universityID: UniversityID): DBIO[Seq[StoredClientSummaryVersion]]
  def findAtRiskQuery(riskStatues: Set[ClientRiskStatus]): Query[ClientSummaries, StoredClientSummary, Seq]
}

object ClientSummaryDao {
  case class StoredClientSummary(
    universityID: UniversityID,
    notes: String,
    alternativeContactNumber: String,
    alternativeEmailAddress: String,
    riskStatus: Option[ClientRiskStatus],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredClientSummary] {
    override def atVersion(at: OffsetDateTime): StoredClientSummary = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredClientSummary]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredClientSummaryVersion(
        universityID,
        notes,
        alternativeContactNumber,
        alternativeEmailAddress,
        riskStatus,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def asClientSummary(client: Client, reasonableAdjustments: Set[ReasonableAdjustment]) = ClientSummary(
      client = client,
      notes = notes,
      alternativeContactNumber = alternativeContactNumber,
      alternativeEmailAddress = alternativeEmailAddress,
      riskStatus = riskStatus,
      reasonableAdjustments = reasonableAdjustments,
      updatedDate = version
    )
  }

  case class StoredClientSummaryVersion(
    universityId: UniversityID,
    notes: String,
    alternativeContactNumber: String,
    alternativeEmailAddress: String,
    riskStatus: Option[ClientRiskStatus],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredClientSummary]

  object StoredClientSummary extends Versioning {
    def tupled: ((UniversityID, String, String, String, Option[ClientRiskStatus], OffsetDateTime)) => StoredClientSummary = (StoredClientSummary.apply _).tupled

    sealed trait CommonClientSummaryProperties { self: Table[_] =>
      def notes: Rep[String] = column[String]("notes")
      def alternativeContactNumber: Rep[String] = column[String]("alt_contact_number")
      def alternativeEmailAddress: Rep[String] = column[String]("alt_email")
      def riskStatus: Rep[Option[ClientRiskStatus]] = column[Option[ClientRiskStatus]]("risk_status")
      def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
    }

    class ClientSummaries(tag: Tag) extends Table[StoredClientSummary](tag, "client_summary") with VersionedTable[StoredClientSummary] with CommonClientSummaryProperties {
      override def matchesPrimaryKey(other: StoredClientSummary): Rep[Boolean] = universityID === other.universityID

      def universityID: Rep[UniversityID] = column[UniversityID]("university_id", O.PrimaryKey)

      def * : ProvenShape[StoredClientSummary] = (universityID, notes, alternativeContactNumber, alternativeEmailAddress, riskStatus, version).mapTo[StoredClientSummary]
    }

    class ClientSummaryVersions(tag: Tag) extends Table[StoredClientSummaryVersion](tag, "client_summary_version") with StoredVersionTable[StoredClientSummary] with CommonClientSummaryProperties {
      def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
      def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
      def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")

      def * : ProvenShape[StoredClientSummaryVersion] = (universityID, notes, alternativeContactNumber, alternativeEmailAddress, riskStatus, version, operation, timestamp, auditUser).mapTo[StoredClientSummaryVersion]
      def pk: PrimaryKey = primaryKey("pk_client_summary_version", (universityID, timestamp))
      def idx: Index = index("idx_client_summary_version", (universityID, version))
    }

    implicit class ClientSummaryExtensions[C[_]](q: Query[ClientSummaries, StoredClientSummary, C]) {
      def withClient = q
        .join(ClientDao.clients.table)
        .on(_.universityID === _.universityID)
    }

    val clientSummaries: VersionedTableQuery[StoredClientSummary, StoredClientSummaryVersion, ClientSummaries, ClientSummaryVersions] =
      VersionedTableQuery[StoredClientSummary, StoredClientSummaryVersion, ClientSummaries, ClientSummaryVersions](TableQuery[ClientSummaries], TableQuery[ClientSummaryVersions])

    case class StoredReasonableAdjustment(
      universityID: UniversityID,
      reasonableAdjustment: ReasonableAdjustment,
      version: OffsetDateTime = JavaTime.offsetDateTime,
    ) extends Versioned[StoredReasonableAdjustment] {
      override def atVersion(at: OffsetDateTime): StoredReasonableAdjustment = copy(version = at)
      override def storedVersion[B <: StoredVersion[StoredReasonableAdjustment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
        StoredReasonableAdjustmentVersion(
          universityID,
          reasonableAdjustment,
          version,
          operation,
          timestamp,
          ac.usercode
        ).asInstanceOf[B]
    }

    case class StoredReasonableAdjustmentVersion(
      universityID: UniversityID,
      reasonableAdjustment: ReasonableAdjustment,
      version: OffsetDateTime = JavaTime.offsetDateTime,
      operation: DatabaseOperation,
      timestamp: OffsetDateTime,
      auditUser: Option[Usercode]
    ) extends StoredVersion[StoredReasonableAdjustment]

    trait CommonStoredReasonableAdjustmentProperties { self: Table[_] =>
      def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
      def reasonableAdjustment = column[ReasonableAdjustment]("reasonable_adjustment")
      def version = column[OffsetDateTime]("version_utc")
    }

    class ReasonableAdjustments(tag: Tag) extends Table[StoredReasonableAdjustment](tag, "reasonable_adjustment")
      with VersionedTable[StoredReasonableAdjustment]
      with CommonStoredReasonableAdjustmentProperties {
      override def matchesPrimaryKey(other: StoredReasonableAdjustment): Rep[Boolean] =
        universityID === other.universityID && reasonableAdjustment === other.reasonableAdjustment

      override def * : ProvenShape[StoredReasonableAdjustment] =
        (universityID, reasonableAdjustment, version).mapTo[StoredReasonableAdjustment]
      def pk = primaryKey("pk_reasonable_adjustment", (universityID, reasonableAdjustment))
      def fk = foreignKey("fk_reasonable_adjustment", universityID, clientSummaries.table)(_.universityID)
      def idx = index("idx_reasonable_adjustment", universityID)
    }

    class ReasonableAdjustmentVersions(tag: Tag) extends Table[StoredReasonableAdjustmentVersion](tag, "reasonable_adjustment_version")
      with StoredVersionTable[StoredReasonableAdjustment]
      with CommonStoredReasonableAdjustmentProperties {
      def operation = column[DatabaseOperation]("version_operation")
      def timestamp = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")

      override def * : ProvenShape[StoredReasonableAdjustmentVersion] =
        (universityID, reasonableAdjustment, version, operation, timestamp, auditUser).mapTo[StoredReasonableAdjustmentVersion]
      def pk = primaryKey("pk_reasonable_adjustment_version", (universityID, reasonableAdjustment, timestamp))
      def idx = index("idx_reasonable_adjustment_version", (universityID, reasonableAdjustment, version))
    }

    val reasonableAdjustments: VersionedTableQuery[StoredReasonableAdjustment, StoredReasonableAdjustmentVersion, ReasonableAdjustments, ReasonableAdjustmentVersions] =
      VersionedTableQuery[StoredReasonableAdjustment, StoredReasonableAdjustmentVersion, ReasonableAdjustments, ReasonableAdjustmentVersions](TableQuery[ReasonableAdjustments], TableQuery[ReasonableAdjustmentVersions])
  }
}

@Singleton
class ClientSummaryDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends ClientSummaryDao with HasDatabaseConfigProvider[JdbcProfile] {

  import StoredClientSummary._
  import dbConfig.profile.api._

  override def insert(summary: StoredClientSummary)(implicit ac: AuditLogContext): DBIO[StoredClientSummary] =
    clientSummaries.insert(summary)

  override def update(summary: StoredClientSummary, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClientSummary] =
    clientSummaries.update(summary.copy(version = version))

  override def insertReasonableAdjustments(adjustments: Set[StoredReasonableAdjustment])(implicit ac: AuditLogContext): DBIO[Seq[StoredReasonableAdjustment]] =
    reasonableAdjustments.insertAll(adjustments.toSeq)

  override def deleteReasonableAdjustments(adjustments: Set[StoredReasonableAdjustment])(implicit ac: AuditLogContext): DBIO[Done] =
    reasonableAdjustments.deleteAll(adjustments.toSeq)

  override def get(universityID: UniversityID): DBIO[Option[(StoredClientSummary, StoredClient)]] =
    clientSummaries.table.filter(_.universityID === universityID).withClient.take(1).result.headOption

  override def getByAlternativeEmailAddress(email: String): DBIO[Option[(StoredClientSummary, StoredClient)]] =
    clientSummaries.table.filter(_.alternativeEmailAddress === email).withClient.take(1).result.headOption

  override def getReasonableAdjustmentsQuery(universityID: UniversityID): Query[ReasonableAdjustments, StoredReasonableAdjustment, Seq] =
    reasonableAdjustments.table
      .filter(_.universityID === universityID)

  override def getHistory(universityID: UniversityID): _root_.domain.ExtendedPostgresProfile.api.DBIO[Seq[StoredClientSummaryVersion]] =
    clientSummaries.history(_.universityID === universityID)

  override def findAtRiskQuery(riskStatues: Set[ClientRiskStatus]): Query[ClientSummaries, StoredClientSummary, Seq] =
    clientSummaries.table
      .filter(c => c.riskStatus.inSet(riskStatues))

}

