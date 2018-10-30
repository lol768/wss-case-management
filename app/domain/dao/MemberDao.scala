package domain.dao

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.MemberDao.StoredMember
import domain.dao.MemberDao.StoredMember.{MemberVersions, Members, VersionedTableQuery}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[MemberDaoImpl])
trait MemberDao {
  def insert(member: StoredMember)(implicit ac: AuditLogContext): DBIO[StoredMember]
  def update(member: StoredMember, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredMember]
  def get(usercode: Usercode): DBIO[Option[StoredMember]]
  def get(usercodes: Set[Usercode]): DBIO[Seq[StoredMember]]
  def getOlderThan(duration: FiniteDuration): Query[Members, StoredMember, Seq]
  def findByNameQuery(name: String): Query[Members, StoredMember, Seq]
}

object MemberDao {
  case class StoredMember(
    usercode: Usercode,
    fullName: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredMember] {
    override def atVersion(at: OffsetDateTime): StoredMember = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredMember]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredMemberVersion(
        usercode,
        fullName,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def asMember = Member(
      usercode = usercode,
      fullName = fullName,
      lastUpdated = version
    )
  }

  case class StoredMemberVersion(
    usercode: Usercode,
    fullName: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredMember]

  object StoredMember extends Versioning {
    def tupled: ((Usercode, Option[String], OffsetDateTime)) => StoredMember = (StoredMember.apply _).tupled

    sealed trait CommonProperties { self: Table[_] =>
      def fullName: Rep[Option[String]] = column[Option[String]]("full_name")
      def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
    }

    class Members(tag: Tag) extends Table[StoredMember](tag, "member") with VersionedTable[StoredMember] with CommonProperties {
      override def matchesPrimaryKey(other: StoredMember): Rep[Boolean] = usercode === other.usercode

      def usercode: Rep[Usercode] = column[Usercode]("user_id", O.PrimaryKey)

      def * : ProvenShape[StoredMember] = (usercode, fullName, version).mapTo[StoredMember]
    }

    class MemberVersions(tag: Tag) extends Table[StoredMemberVersion](tag, "member_version") with StoredVersionTable[StoredMember] with CommonProperties {
      def usercode: Rep[Usercode] = column[Usercode]("user_id")
      def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
      def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")

      def * : ProvenShape[StoredMemberVersion] = (usercode, fullName, version, operation, timestamp, auditUser).mapTo[StoredMemberVersion]
      def pk: PrimaryKey = primaryKey("pk_member_version", (usercode, timestamp))
      def idx: Index = index("idx_member_version", (usercode, version))
    }

  }

  val members: VersionedTableQuery[StoredMember, StoredMemberVersion, Members, MemberVersions] =
    VersionedTableQuery[StoredMember, StoredMemberVersion, Members, MemberVersions](TableQuery[Members], TableQuery[MemberVersions])
}

class MemberDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends MemberDao with HasDatabaseConfigProvider[JdbcProfile] {
  import dbConfig.profile.api._
  import domain.dao.MemberDao._

  override def insert(member: StoredMember)(implicit ac: AuditLogContext): DBIO[StoredMember] =
    members.insert(member)

  override def update(member: StoredMember, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredMember] =
    members.update(member.copy(version = version))

  override def get(usercode: Usercode): DBIO[Option[StoredMember]] =
    members.table.filter(_.usercode === usercode).take(1).result.headOption

  override def get(usercodes: Set[Usercode]): DBIO[Seq[StoredMember]] =
    members.table.filter(_.usercode.inSet(usercodes)).result

  override def getOlderThan(duration: FiniteDuration): Query[Members, StoredMember, Seq] =
    members.table.filter(_.version < JavaTime.offsetDateTime.minus(duration.toDays, ChronoUnit.DAYS))

  override def findByNameQuery(name: String): Query[Members, StoredMember, Seq] =
    members.table.filter(m =>
      m.fullName.nonEmpty &&
        m.fullName.toLowerCase.like(
          name.split(" ").map(_.trim.toLowerCase).map(n => s"%$n%").mkString("")
        )
    )
}
