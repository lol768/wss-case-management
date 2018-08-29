package domain.dao

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.ClientSummaryDao.PersistedClientSummary
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ClientSummaryDaoImpl])
trait ClientSummaryDao {
  def insert(universityID: UniversityID, data: ClientSummaryData): DBIO[PersistedClientSummary]
  def update(universityID: UniversityID, data: ClientSummaryData, version: OffsetDateTime): DBIO[PersistedClientSummary]
  def get(universityID: UniversityID): DBIO[Option[PersistedClientSummary]]
  def all: DBIO[Seq[PersistedClientSummary]]
}

object ClientSummaryDao {
  case class PersistedClientSummary(
    universityID: UniversityID,
    highMentalHealthRisk: Option[Boolean],
    data: JsValue,
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[PersistedClientSummary] {
    override def atVersion(at: OffsetDateTime): PersistedClientSummary = copy(version = at)

    override def storedVersion[B <: StoredVersion[PersistedClientSummary]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      PersistedClientSummaryVersion(
        universityID,
        highMentalHealthRisk,
        data,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]

    def parsed = ClientSummary(
      universityID = universityID,
      updatedDate = version,
      data = ClientSummaryData(
        highMentalHealthRisk = highMentalHealthRisk,
        notes = data("notes").as[String],
        alternativeContactNumber = data("alternativeContactNumber").as[String],
        alternativeEmailAddress = data("alternativeEmailAddress").as[String],
        riskStatus = data("riskStatus").asOpt[ClientRiskStatus],
        reasonableAdjustments = data("reasonableAdjustments").as[Set[ReasonableAdjustment]]
      )
    )
  }

  case class PersistedClientSummaryVersion(
    universityId: UniversityID,
    highMentalHealthRisk: Option[Boolean],
    data: JsValue,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[PersistedClientSummary]

  object PersistedClientSummary extends Versioning {
    def tupled: ((UniversityID, Option[Boolean], JsValue, OffsetDateTime)) => PersistedClientSummary = (PersistedClientSummary.apply _).tupled

    sealed trait CommonProperties { self: Table[_] =>
      def highMentalHealthRisk: Rep[Option[Boolean]] = column[Option[Boolean]]("mental_health_risk")
      def data: Rep[JsValue] = column[JsValue]("data")
      def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
    }

    class PersistedClientSummaries(tag: Tag) extends Table[PersistedClientSummary](tag, "client_summary") with VersionedTable[PersistedClientSummary] with CommonProperties {
      override def matchesPrimaryKey(other: PersistedClientSummary): Rep[Boolean] = universityID === other.universityID

      def universityID: Rep[UniversityID] = column[UniversityID]("university_id", O.PrimaryKey)

      def * : ProvenShape[PersistedClientSummary] = (universityID, highMentalHealthRisk, data, version).mapTo[PersistedClientSummary]
    }

    class PersistedClientSummaryVersions(tag: Tag) extends Table[PersistedClientSummaryVersion](tag, "client_summary_version") with StoredVersionTable[PersistedClientSummary] with CommonProperties {
      def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
      def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
      def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")

      def * : ProvenShape[PersistedClientSummaryVersion] = (universityID, highMentalHealthRisk, data, version, operation, timestamp).mapTo[PersistedClientSummaryVersion]
      def pk: PrimaryKey = primaryKey("pk_client_summary_version", (universityID, timestamp))
      def idx: Index = index("idx_client_summary_version", (universityID, version))
    }

    val clientSummaries: VersionedTableQuery[PersistedClientSummary, PersistedClientSummaryVersion, PersistedClientSummaries, PersistedClientSummaryVersions] =
      VersionedTableQuery[PersistedClientSummary, PersistedClientSummaryVersion, PersistedClientSummaries, PersistedClientSummaryVersions](TableQuery[PersistedClientSummaries], TableQuery[PersistedClientSummaryVersions])
  }
}

@Singleton
class ClientSummaryDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends ClientSummaryDao with HasDatabaseConfigProvider[JdbcProfile] {

  import PersistedClientSummary._
  import dbConfig.profile.api._

  override def insert(universityID: UniversityID, data: ClientSummaryData): DBIO[PersistedClientSummary] =
    clientSummaries.insert(PersistedClientSummary(
      universityID,
      data.highMentalHealthRisk,
      data.getJsonFields
    ))

  override def update(universityID: UniversityID, data: ClientSummaryData, version: OffsetDateTime): DBIO[PersistedClientSummary] =
    clientSummaries.update(PersistedClientSummary(
      universityID,
      data.highMentalHealthRisk,
      data.getJsonFields,
      version
    ))

  override def get(universityID: UniversityID): DBIO[Option[PersistedClientSummary]] =
    clientSummaries.table.filter(_.universityID === universityID).take(1).result.headOption

  override def all: DBIO[Seq[PersistedClientSummary]] =
    clientSummaries.table.result

}

