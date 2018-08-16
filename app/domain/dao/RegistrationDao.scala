package domain.dao

import java.time.ZonedDateTime

import com.google.inject.{ImplementedBy, Inject}
import domain.CustomJdbcTypes._
import domain._
import helpers.JavaTime
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

object RegistrationDao {

  case class Registration(
    universityID: UniversityID,
    data: JsValue,
    version: ZonedDateTime = JavaTime.zonedDateTime
  ) extends Versioned[Registration] {

    override def atVersion(at: ZonedDateTime): Registration = copy(version = at)

    override def storedVersion[B <: StoredVersion[Registration]](operation: DatabaseOperation, timestamp: ZonedDateTime): B =
      RegistrationVersion(
        universityID,
        data,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]

    def parsed = domain.Registration(
      universityID = this.universityID,
      updatedDate = this.version,
      data = this.data.validate[domain.RegistrationData](domain.RegistrationData.formatter).get
    )
  }

  case class RegistrationVersion(
    universityId: UniversityID,
    data: JsValue,
    version: ZonedDateTime = JavaTime.zonedDateTime,
    operation: DatabaseOperation,
    timestamp: ZonedDateTime
  ) extends StoredVersion[Registration]

  object Registration extends Versioning {
    def tupled: ((UniversityID, JsValue, ZonedDateTime)) => Registration = (Registration.apply _).tupled

    sealed trait CommonProperties { self: Table[_] =>
      def data = column[JsValue]("data")
      def version = column[ZonedDateTime]("version")
    }

    class Registrations(tag: Tag) extends Table[Registration](tag, "user_registration") with VersionedTable[Registration] with CommonProperties {
      override def matchesPrimaryKey(other: Registration): Rep[Boolean] = universityID === other.universityID

      def universityID = column[UniversityID]("university_id", O.PrimaryKey)

      def * = (universityID, data, version).mapTo[Registration]
    }

    class RegistrationVersions(tag: Tag) extends Table[RegistrationVersion](tag, "user_registration_version") with StoredVersionTable[Registration] with CommonProperties {
      def universityID = column[UniversityID]("university_id")
      def operation = column[DatabaseOperation]("version_operation")
      def timestamp = column[ZonedDateTime]("version_timestamp")

      def * = (universityID, data, version, operation, timestamp).mapTo[RegistrationVersion]
      def pk = primaryKey("pk_user_registration_versions", (universityID, timestamp))
      def idx = index("idx_user_registration_versions", (universityID, version))
    }

    val registrations: VersionedTableQuery[Registration, RegistrationVersion, Registrations, RegistrationVersions] =
      VersionedTableQuery[Registration, RegistrationVersion, Registrations, RegistrationVersions](TableQuery[Registrations], TableQuery[RegistrationVersions])

  }
}

@ImplementedBy(classOf[RegistrationDaoImpl])
trait RegistrationDao {

  def insert(universityID: UniversityID, data: domain.RegistrationData): DBIO[RegistrationDao.Registration]

  def update(universityID: UniversityID, data: domain.RegistrationData, version: ZonedDateTime): DBIO[RegistrationDao.Registration]

  def get(universityID: UniversityID): DBIO[Option[RegistrationDao.Registration]]

}

class RegistrationDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext)
  extends RegistrationDao with HasDatabaseConfigProvider[JdbcProfile] {

  import dbConfig.profile.api._

  override def insert(universityID: UniversityID, data: domain.RegistrationData): DBIO[RegistrationDao.Registration] =
    RegistrationDao.Registration.registrations.insert(RegistrationDao.Registration(
      universityID,
      Json.toJson(data)(domain.RegistrationData.formatter)
    ))

  override def update(universityID: UniversityID, data: domain.RegistrationData, version: ZonedDateTime): DBIO[RegistrationDao.Registration] =
    RegistrationDao.Registration.registrations.update(RegistrationDao.Registration(
      universityID,
      Json.toJson(data)(domain.RegistrationData.formatter),
      version
    ))

  override def get(universityID: UniversityID): DBIO[Option[RegistrationDao.Registration]] =
    RegistrationDao.Registration.registrations.table.filter(_.universityID === universityID).take(1).result.headOption

}
