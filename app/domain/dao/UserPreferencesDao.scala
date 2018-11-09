package domain.dao

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.UserPreferencesDao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[UserPreferencesDaoImpl])
trait UserPreferencesDao {
  def find(usercode: Usercode): DBIO[Option[StoredUserPreferences]]
  def find(usercodes: Set[Usercode]): DBIO[Seq[StoredUserPreferences]]
  def upsert(preferences: StoredUserPreferences)(implicit ac: AuditLogContext): DBIO[StoredUserPreferences]
}

@Singleton
class UserPreferencesDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends UserPreferencesDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def find(usercode: Usercode): DBIO[Option[StoredUserPreferences]] =
    userPreferences.table.filter(_.usercode === usercode).take(1).result.headOption

  override def find(usercodes: Set[Usercode]): DBIO[Seq[StoredUserPreferences]] =
    userPreferences.table.filter(_.usercode.inSet(usercodes)).result

  override def upsert(p: StoredUserPreferences)(implicit ac: AuditLogContext): DBIO[StoredUserPreferences] =
    // We avoid doing a native upsert as it makes the versioning table tricky (does the versioning table get an insert or an update?)
    for {
      existing <- find(p.usercode)
      upserted <- existing match {
        case Some(e) =>
          // Prevent optimistic locking exception by using the same version
          userPreferences.update(e.copy(preferences = p.preferences))

        case _ => userPreferences.insert(p)
      }
    } yield upserted

}

object UserPreferencesDao {
  val userPreferences: VersionedTableQuery[StoredUserPreferences, StoredUserPreferencesVersion, UserPreferences, UserPreferencesVersions] =
    VersionedTableQuery(TableQuery[UserPreferences], TableQuery[UserPreferencesVersions])

  case class StoredUserPreferences(
    usercode: Usercode,
    preferences: JsValue,
    version: OffsetDateTime,
  ) extends Versioned[StoredUserPreferences] {
    def parsed: domain.UserPreferences =
      preferences.validate[domain.UserPreferences](domain.UserPreferences.formatter).get

    override def atVersion(at: OffsetDateTime): StoredUserPreferences = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredUserPreferences]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredUserPreferencesVersion(
        usercode,
        preferences,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredUserPreferencesVersion(
    usercode: Usercode,
    preferences: JsValue,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredUserPreferences]

  trait CommonProperties { self: Table[_] =>
    def preferences = column[JsValue]("preferences")
    def version = column[OffsetDateTime]("version_utc")
  }

  class UserPreferences(tag: Tag) extends Table[StoredUserPreferences](tag, "user_preferences")
    with VersionedTable[StoredUserPreferences]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredUserPreferences): Rep[Boolean] = usercode === other.usercode
    def usercode = column[Usercode]("user_id", O.PrimaryKey)

    override def * : ProvenShape[StoredUserPreferences] =
      (usercode, preferences, version).mapTo[StoredUserPreferences]
  }

  class UserPreferencesVersions(tag: Tag) extends Table[StoredUserPreferencesVersion](tag, "user_preferences_version")
    with StoredVersionTable[StoredUserPreferences]
    with CommonProperties {
    def usercode = column[Usercode]("user_id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredUserPreferencesVersion] =
      (usercode, preferences, version, operation, timestamp, auditUser).mapTo[StoredUserPreferencesVersion]
    def pk = primaryKey("pk_user_preferences_version", (usercode, timestamp))
    def idx = index("idx_user_preferences_version", (usercode, version))
  }
}