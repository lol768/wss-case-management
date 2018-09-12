package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.OutgoingEmailDao.PersistedOutgoingEmail
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.Email
import slick.jdbc.{JdbcProfile, PostgresProfile}
import ExtendedPostgresProfile.api._
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[OutgoingEmailDaoImpl])
trait OutgoingEmailDao {
  def insert(email: OutgoingEmail): DBIO[PersistedOutgoingEmail]
  def insertAll(emails: Seq[OutgoingEmail]): DBIO[Seq[PersistedOutgoingEmail]]
  def update(email: OutgoingEmail, version: OffsetDateTime): DBIO[PersistedOutgoingEmail]
  def get(id: UUID): DBIO[Option[PersistedOutgoingEmail]]
  def countUnsentEmails(): DBIO[Int]
}

object OutgoingEmailDao {
  case class PersistedOutgoingEmail(
    id: UUID,
    created: OffsetDateTime,
    email: JsValue,
    recipient: Option[Usercode],
    emailAddress: Option[String],
    sent: Option[OffsetDateTime],
    lastSendAttempt: Option[OffsetDateTime],
    failureReason: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[PersistedOutgoingEmail] {
    override def atVersion(at: OffsetDateTime): PersistedOutgoingEmail = copy(version = at)
    override def storedVersion[B <: StoredVersion[PersistedOutgoingEmail]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      PersistedOutgoingEmailVersion.versioned(this, operation, timestamp).asInstanceOf[B]

    def parsed: OutgoingEmail = OutgoingEmail(
      Some(id),
      created,
      email.validate[Email](OutgoingEmail.emailFormatter).get,
      recipient,
      emailAddress,
      sent,
      lastSendAttempt,
      failureReason,
      version
    )
  }

  case class PersistedOutgoingEmailVersion(
    id: UUID,
    created: OffsetDateTime,
    email: JsValue,
    recipient: Option[Usercode],
    emailAddress: Option[String],
    sent: Option[OffsetDateTime],
    lastSendAttempt: Option[OffsetDateTime],
    failureReason: Option[String],
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[PersistedOutgoingEmail]

  object PersistedOutgoingEmailVersion {
    def tupled = (apply _).tupled

    def versioned(email: PersistedOutgoingEmail, operation: DatabaseOperation, timestamp: OffsetDateTime): PersistedOutgoingEmailVersion =
      PersistedOutgoingEmailVersion(
        email.id,
        email.created,
        email.email,
        email.recipient,
        email.emailAddress,
        email.sent,
        email.lastSendAttempt,
        email.failureReason,
        email.version,
        operation,
        timestamp
      )
  }

  object PersistedOutgoingEmail extends Versioning {
    def tupled = (apply _).tupled

    sealed trait CommonProperties {
      self: Table[_] =>

      def created = column[OffsetDateTime]("created_utc")
      def email = column[JsValue]("email")
      def recipient = column[Usercode]("recipient")
      def emailAddress = column[String]("recipient_email")
      def sent = column[OffsetDateTime]("sent_at_utc")
      def lastSendAttempt = column[OffsetDateTime]("last_send_attempt_at_utc")
      def failureReason = column[String]("failure_reason")
      def version = column[OffsetDateTime]("version_utc")
    }

    class OutgoingEmails(tag: Tag) extends Table[PersistedOutgoingEmail](tag, "outgoing_email") with VersionedTable[PersistedOutgoingEmail] with CommonProperties {
      override def matchesPrimaryKey(other: PersistedOutgoingEmail): Rep[Boolean] = id === other.id

      def id = column[UUID]("id", O.PrimaryKey)

      def * = (id, created, email, recipient.?, emailAddress.?, sent.?, lastSendAttempt.?, failureReason.?, version).mapTo[PersistedOutgoingEmail]
    }

    class OutgoingEmailVersions(tag: Tag) extends Table[PersistedOutgoingEmailVersion](tag, "outgoing_email_version") with StoredVersionTable[PersistedOutgoingEmail] with CommonProperties {
      def id = column[UUID]("id")
      def operation = column[DatabaseOperation]("version_operation")
      def timestamp = column[OffsetDateTime]("version_timestamp_utc")

      def * = (id, created, email, recipient.?, emailAddress.?, sent.?, lastSendAttempt.?, failureReason.?, version, operation, timestamp).mapTo[PersistedOutgoingEmailVersion]
      def pk = primaryKey("pk_outgoing_email_version", (id, timestamp))
      def idx = index("idx_outgoing_email_version", (id, version))
    }

    val outgoingEmails: VersionedTableQuery[PersistedOutgoingEmail, PersistedOutgoingEmailVersion, OutgoingEmails, OutgoingEmailVersions] =
      VersionedTableQuery(TableQuery[OutgoingEmails], TableQuery[OutgoingEmailVersions])
  }
}

@Singleton
class OutgoingEmailDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends OutgoingEmailDao with HasDatabaseConfigProvider[JdbcProfile] {

  import PersistedOutgoingEmail._
  import dbConfig.profile.api._

  override def insert(email: OutgoingEmail): DBIO[PersistedOutgoingEmail] =
    outgoingEmails.insert(PersistedOutgoingEmail(
      UUID.randomUUID(),
      email.created,
      Json.toJson(email.email)(OutgoingEmail.emailFormatter),
      email.recipient,
      email.emailAddress,
      email.sent,
      email.lastSendAttempt,
      email.failureReason
    ))


  override def insertAll(emails: Seq[OutgoingEmail]): PostgresProfile.api.DBIO[Seq[PersistedOutgoingEmail]] =
    outgoingEmails.insertAll(emails.map { email =>
      PersistedOutgoingEmail(
        UUID.randomUUID(),
        email.created,
        Json.toJson(email.email)(OutgoingEmail.emailFormatter),
        email.recipient,
        email.emailAddress,
        email.sent,
        email.lastSendAttempt,
        email.failureReason
      )
    })

  override def update(email: OutgoingEmail, version: OffsetDateTime): DBIO[PersistedOutgoingEmail] =
    outgoingEmails.update(PersistedOutgoingEmail(
      email.id.get,
      email.created,
      Json.toJson(email.email)(OutgoingEmail.emailFormatter),
      email.recipient,
      email.emailAddress,
      email.sent,
      email.lastSendAttempt,
      email.failureReason,
      version
    ))

  override def get(id: UUID): DBIO[Option[PersistedOutgoingEmail]] =
    outgoingEmails.table.filter(_.id === id).take(1).result.headOption

  override def countUnsentEmails(): PostgresProfile.api.DBIO[Int] =
    outgoingEmails.table.filter { e => e.sent.?.isEmpty && (e.lastSendAttempt.?.nonEmpty || e.failureReason.?.isEmpty) }.length.result

}
