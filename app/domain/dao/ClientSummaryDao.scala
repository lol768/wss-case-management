package domain.dao

import java.time.OffsetDateTime

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.ClientSummaryDao.StoredClientSummary
import domain.dao.ClientSummaryDao.StoredClientSummary.{ReasonableAdjustments, StoredReasonableAdjustment}
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ClientSummaryDaoImpl])
trait ClientSummaryDao {
  def insert(summary: StoredClientSummary): DBIO[StoredClientSummary]
  def update(summary: StoredClientSummary, version: OffsetDateTime): DBIO[StoredClientSummary]
  def insertReasonableAdjustments(reasonableAdjustments: Set[StoredReasonableAdjustment]): DBIO[Seq[StoredReasonableAdjustment]]
  def insertReasonableAdjustment(reasonableAdjustment: StoredReasonableAdjustment): DBIO[StoredReasonableAdjustment]
  def deleteReasonableAdjustment(reasonableAdjustment: StoredReasonableAdjustment): DBIO[Done]
  def get(universityID: UniversityID): DBIO[Option[StoredClientSummary]]
  def getByAlternativeEmailAddress(email: String): DBIO[Option[StoredClientSummary]]
  def getReasonableAdjustmentsQuery(universityID: UniversityID): Query[ReasonableAdjustments, StoredReasonableAdjustment, Seq]
  def all: DBIO[Seq[StoredClientSummary]]
}

object ClientSummaryDao {
  case class StoredClientSummary(
    universityID: UniversityID,
    highMentalHealthRisk: Option[Boolean],
    notes: String,
    alternativeContactNumber: String,
    alternativeEmailAddress: String,
    riskStatus: Option[ClientRiskStatus],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredClientSummary] {
    override def atVersion(at: OffsetDateTime): StoredClientSummary = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredClientSummary]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredClientSummaryVersion(
        universityID,
        highMentalHealthRisk,
        notes,
        alternativeContactNumber,
        alternativeEmailAddress,
        riskStatus,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]

    def asClientSummary(reasonableAdjustments: Set[ReasonableAdjustment]) = ClientSummary(
      universityID = universityID,
      highMentalHealthRisk = highMentalHealthRisk,
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
    highMentalHealthRisk: Option[Boolean],
    notes: String,
    alternativeContactNumber: String,
    alternativeEmailAddress: String,
    riskStatus: Option[ClientRiskStatus],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[StoredClientSummary]

  object StoredClientSummary extends Versioning {
    def tupled: ((UniversityID, Option[Boolean], String, String, String, Option[ClientRiskStatus], OffsetDateTime)) => StoredClientSummary = (StoredClientSummary.apply _).tupled

    sealed trait CommonClientSummaryProperties { self: Table[_] =>
      def highMentalHealthRisk: Rep[Option[Boolean]] = column[Option[Boolean]]("mental_health_risk")
      def notes: Rep[String] = column[String]("notes")
      def alternativeContactNumber: Rep[String] = column[String]("alt_contact_number")
      def alternativeEmailAddress: Rep[String] = column[String]("alt_email")
      def riskStatus: Rep[Option[ClientRiskStatus]] = column[Option[ClientRiskStatus]]("risk_status")
      def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
    }

    class ClientSummaries(tag: Tag) extends Table[StoredClientSummary](tag, "client_summary") with VersionedTable[StoredClientSummary] with CommonClientSummaryProperties {
      override def matchesPrimaryKey(other: StoredClientSummary): Rep[Boolean] = universityID === other.universityID

      def universityID: Rep[UniversityID] = column[UniversityID]("university_id", O.PrimaryKey)

      def * : ProvenShape[StoredClientSummary] = (universityID, highMentalHealthRisk, notes, alternativeContactNumber, alternativeEmailAddress, riskStatus, version).mapTo[StoredClientSummary]
    }

    class ClientSummaryVersions(tag: Tag) extends Table[StoredClientSummaryVersion](tag, "client_summary_version") with StoredVersionTable[StoredClientSummary] with CommonClientSummaryProperties {
      def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
      def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
      def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")

      def * : ProvenShape[StoredClientSummaryVersion] = (universityID, highMentalHealthRisk, notes, alternativeContactNumber, alternativeEmailAddress, riskStatus, version, operation, timestamp).mapTo[StoredClientSummaryVersion]
      def pk: PrimaryKey = primaryKey("pk_client_summary_version", (universityID, timestamp))
      def idx: Index = index("idx_client_summary_version", (universityID, version))
    }

    val clientSummaries: VersionedTableQuery[StoredClientSummary, StoredClientSummaryVersion, ClientSummaries, ClientSummaryVersions] =
      VersionedTableQuery[StoredClientSummary, StoredClientSummaryVersion, ClientSummaries, ClientSummaryVersions](TableQuery[ClientSummaries], TableQuery[ClientSummaryVersions])

    case class StoredReasonableAdjustment(
      universityID: UniversityID,
      reasonableAdjustment: ReasonableAdjustment,
      version: OffsetDateTime = OffsetDateTime.now()
    ) extends Versioned[StoredReasonableAdjustment] {
      override def atVersion(at: OffsetDateTime): StoredReasonableAdjustment = copy(version = at)
      override def storedVersion[B <: StoredVersion[StoredReasonableAdjustment]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
        StoredReasonableAdjustmentVersion(
          universityID,
          reasonableAdjustment,
          version,
          operation,
          timestamp
        ).asInstanceOf[B]
    }

    case class StoredReasonableAdjustmentVersion(
      universityID: UniversityID,
      reasonableAdjustment: ReasonableAdjustment,
      version: OffsetDateTime = OffsetDateTime.now(),
      operation: DatabaseOperation,
      timestamp: OffsetDateTime
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

      override def * : ProvenShape[StoredReasonableAdjustmentVersion] =
        (universityID, reasonableAdjustment, version, operation, timestamp).mapTo[StoredReasonableAdjustmentVersion]
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

  override def insert(summary: StoredClientSummary): DBIO[StoredClientSummary] =
    clientSummaries.insert(summary)

  override def update(summary: StoredClientSummary, version: OffsetDateTime): DBIO[StoredClientSummary] =
    clientSummaries.update(summary.copy(version = version))

  override def insertReasonableAdjustments(adjustments: Set[StoredReasonableAdjustment]): DBIO[Seq[StoredReasonableAdjustment]] =
    reasonableAdjustments.insertAll(adjustments.toSeq)

  override def insertReasonableAdjustment(reasonableAdjustment: StoredReasonableAdjustment): DBIO[StoredReasonableAdjustment] =
    reasonableAdjustments.insert(reasonableAdjustment)

  override def deleteReasonableAdjustment(reasonableAdjustment: StoredReasonableAdjustment): DBIO[Done] =
    reasonableAdjustments.delete(reasonableAdjustment)

  override def get(universityID: UniversityID): DBIO[Option[StoredClientSummary]] =
    clientSummaries.table.filter(_.universityID === universityID).take(1).result.headOption

  override def getByAlternativeEmailAddress(email: String): DBIO[Option[StoredClientSummary]] =
    clientSummaries.table.filter(_.alternativeEmailAddress === email).take(1).result.headOption

  override def getReasonableAdjustmentsQuery(universityID: UniversityID): Query[ReasonableAdjustments, StoredReasonableAdjustment, Seq] =
    reasonableAdjustments.table
      .filter(_.universityID === universityID)

  override def all: DBIO[Seq[StoredClientSummary]] =
    clientSummaries.table.result

}

