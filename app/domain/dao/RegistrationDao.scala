package domain.dao

import java.time.OffsetDateTime

import com.google.inject.{ImplementedBy, Inject}
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import javax.inject.Singleton
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

object RegistrationDao {

  case class Registration(
    universityID: UniversityID,
    data: JsValue,
    lastInvited: OffsetDateTime,
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[Registration] {

    override def atVersion(at: OffsetDateTime): Registration = copy(version = at)

    override def storedVersion[B <: StoredVersion[Registration]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      RegistrationVersion(
        universityID,
        data,
        lastInvited,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def parsed = domain.Registration(
      universityID = this.universityID,
      updatedDate = this.version,
      data = this.data.validateOpt[domain.RegistrationData](domain.RegistrationData.formatter).getOrElse(None),
      lastInvited = this.lastInvited
    )
  }

  case class RegistrationVersion(
    universityId: UniversityID,
    data: JsValue,
    lastInvited: OffsetDateTime,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[Registration]

  object Registration extends Versioning {
    def tupled: ((UniversityID, JsValue, OffsetDateTime, OffsetDateTime)) => Registration = (Registration.apply _).tupled

    sealed trait CommonProperties { self: Table[_] =>
      def data = column[JsValue]("data")
      def lastInvited = column[OffsetDateTime]("last_invited_utc")
      def version = column[OffsetDateTime]("version_utc")
    }

    class Registrations(tag: Tag) extends Table[Registration](tag, "user_registration") with VersionedTable[Registration] with CommonProperties {
      override def matchesPrimaryKey(other: Registration): Rep[Boolean] = universityID === other.universityID

      def universityID = column[UniversityID]("university_id", O.PrimaryKey)

      def * = (universityID, data, lastInvited, version).mapTo[Registration]
    }

    class RegistrationVersions(tag: Tag) extends Table[RegistrationVersion](tag, "user_registration_version") with StoredVersionTable[Registration] with CommonProperties {
      def universityID = column[UniversityID]("university_id")
      def operation = column[DatabaseOperation]("version_operation")
      def timestamp = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")

      def * = (universityID, data, lastInvited, version, operation, timestamp, auditUser).mapTo[RegistrationVersion]
      def pk = primaryKey("pk_user_registration_versions", (universityID, timestamp))
      def idx = index("idx_user_registration_versions", (universityID, version))
    }

    val registrations: VersionedTableQuery[Registration, RegistrationVersion, Registrations, RegistrationVersions] =
      VersionedTableQuery[Registration, RegistrationVersion, Registrations, RegistrationVersions](TableQuery[Registrations], TableQuery[RegistrationVersions])

  }
}

@ImplementedBy(classOf[RegistrationDaoImpl])
trait RegistrationDao {

  def invite(universityID: UniversityID)(implicit ac: AuditLogContext): DBIO[RegistrationDao.Registration]

  def update(universityID: UniversityID, data: Option[domain.RegistrationData], lastInvited: OffsetDateTime, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[RegistrationDao.Registration]

  def get(universityID: UniversityID): DBIO[Option[RegistrationDao.Registration]]

  def getHistory(universityID: UniversityID): DBIO[Seq[(JsValue, OffsetDateTime)]]

}

@Singleton
class RegistrationDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext)
  extends RegistrationDao with HasDatabaseConfigProvider[JdbcProfile] {

  import dbConfig.profile.api._

  override def invite(universityID: UniversityID)(implicit ac: AuditLogContext): DBIO[RegistrationDao.Registration] =
    RegistrationDao.Registration.registrations.insert(RegistrationDao.Registration(
      universityID,
      Json.obj(),
      JavaTime.offsetDateTime
    ))

  override def update(universityID: UniversityID, data: Option[domain.RegistrationData], lastInvited: OffsetDateTime, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[RegistrationDao.Registration] =
    RegistrationDao.Registration.registrations.update(RegistrationDao.Registration(
      universityID,
      data.map(Json.toJson(_)(domain.RegistrationData.formatter)).getOrElse(Json.obj()),
      lastInvited,
      version
    ))

  override def get(universityID: UniversityID): DBIO[Option[RegistrationDao.Registration]] =
    RegistrationDao.Registration.registrations.table.filter(_.universityID === universityID).take(1).result.headOption

  override def getHistory(universityID: UniversityID): DBIO[Seq[(JsValue, OffsetDateTime)]] =
    RegistrationDao.Registration.registrations.versionsTable
      .filter(r =>
        r.universityID === universityID && (
          r.operation === (DatabaseOperation.Insert:DatabaseOperation) ||
          r.operation === (DatabaseOperation.Update:DatabaseOperation)
        )
      )
      .sortBy(_.timestamp)
      .map(v => (v.data, v.timestamp))
      .result

}
