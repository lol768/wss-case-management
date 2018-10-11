package domain.dao

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain._
import helpers.JavaTime
import services.AuditLogContext
import warwick.sso.{UniversityID, Usercode}
import domain.ExtendedPostgresProfile.api._
import domain.CustomJdbcTypes._
import domain.dao.ClientDao.StoredClient
import domain.dao.ClientDao.StoredClient.{ClientVersions, Clients, VersionedTableQuery}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ClientDaoImpl])
trait ClientDao {
  def insert(client: StoredClient)(implicit ac: AuditLogContext): DBIO[StoredClient]
  def update(client: StoredClient, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClient]
  def get(universityID: UniversityID): DBIO[Option[StoredClient]]
}

object ClientDao {
  case class StoredClient(
    universityID: UniversityID,
    fullName: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredClient] {
    override def atVersion(at: OffsetDateTime): StoredClient = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredClient]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredClientVersion(
        universityID,
        fullName,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def asClient = Client(
      universityID = universityID,
      fullName = fullName,
      lastUpdated = version
    )
  }

  case class StoredClientVersion(
    universityID: UniversityID,
    fullName: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredClient]

  object StoredClient extends Versioning {
    def tupled: ((UniversityID, Option[String], OffsetDateTime)) => StoredClient = (StoredClient.apply _).tupled

    sealed trait CommonProperties { self: Table[_] =>
      def fullName: Rep[Option[String]] = column[Option[String]]("full_name")
      def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
    }

    class Clients(tag: Tag) extends Table[StoredClient](tag, "client") with VersionedTable[StoredClient] with CommonProperties {
      override def matchesPrimaryKey(other: StoredClient): Rep[Boolean] = universityID === other.universityID

      def universityID: Rep[UniversityID] = column[UniversityID]("university_id", O.PrimaryKey)

      def * : ProvenShape[StoredClient] = (universityID, fullName, version).mapTo[StoredClient]
    }

    class ClientVersions(tag: Tag) extends Table[StoredClientVersion](tag, "client_version") with StoredVersionTable[StoredClient] with CommonProperties {
      def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
      def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
      def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")

      def * : ProvenShape[StoredClientVersion] = (universityID, fullName, version, operation, timestamp, auditUser).mapTo[StoredClientVersion]
      def pk: PrimaryKey = primaryKey("pk_client_version", (universityID, timestamp))
      def idx: Index = index("idx_client_version", (universityID, version))
    }

  }

  val clients: VersionedTableQuery[StoredClient, StoredClientVersion, Clients, ClientVersions] =
    VersionedTableQuery[StoredClient, StoredClientVersion, Clients, ClientVersions](TableQuery[Clients], TableQuery[ClientVersions])
}

class ClientDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends ClientDao with HasDatabaseConfigProvider[JdbcProfile] {
  import domain.dao.ClientDao._
  import dbConfig.profile.api._

  override def insert(client: StoredClient)(implicit ac: AuditLogContext): DBIO[StoredClient] =
    clients.insert(client)

  override def update(client: StoredClient, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClient] =
    clients.update(client.copy(version = version))

  override def get(universityID: UniversityID): DBIO[Option[StoredClient]] =
    clients.table.filter(_.universityID === universityID).take(1).result.headOption
}
