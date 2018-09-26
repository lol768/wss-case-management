package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.dao.UploadedFileDao
import domain.dao.UploadedFileDao.StoredUploadedFile
import enumeratum._
import helpers.JavaTime
import services.AuditLogContext
import warwick.sso.{UniversityID, Usercode}

import scala.collection.immutable
import scala.language.higherKinds

/**
  * Conversational message which can be attached to an Enquiry or Case.
  * Can be either to or from the client.
  */
case class Message (
  id: UUID,
  text: String,
  sender: MessageSender,
  client: UniversityID,
  teamMember: Option[Usercode],
  team: Option[Team],
  ownerId: UUID,
  ownerType: MessageOwner,
  created: OffsetDateTime = OffsetDateTime.now(),
  version: OffsetDateTime = OffsetDateTime.now()
) extends Versioned[Message] {
  override def atVersion(at: OffsetDateTime): Message = copy(version = at)

  override def storedVersion[B <: StoredVersion[Message]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
    MessageVersion(
      id,
      text,
      sender,
      client,
      teamMember,
      team,
      ownerId,
      ownerType,
      created,
      version,
      operation,
      timestamp,
      ac.usercode
    ).asInstanceOf[B]
}

object Message extends Versioning {
  def tupled = (apply _).tupled

  case class FormData(
    text: String
  )

  sealed trait CommonProperties { self: Table[_] =>
    def text = column[String]("text")
    def searchableText = toTsVector(text, Some("english"))
    def sender = column[MessageSender]("sender")
    def client = column[UniversityID]("university_id")
    def teamMember = column[Option[Usercode]]("team_member")
    def team = column[Option[Team]]("team_id")
    def created = column[OffsetDateTime]("created_utc")
    def ownerId = column[UUID]("owner_id")
    def ownerType = column[MessageOwner]("owner_type")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Messages(tag: Tag) extends Table[Message](tag, "message") with VersionedTable[Message] with CommonProperties {
    override def matchesPrimaryKey(other: Message): Rep[Boolean] = id === other.id

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id, text, sender, client, teamMember, team, ownerId, ownerType, created, version).mapTo[Message]
    def messageData = (text, sender, client, created, teamMember, team).mapTo[MessageData]
  }

  class MessageVersions(tag: Tag) extends Table[MessageVersion](tag, "message_version") with StoredVersionTable[Message] with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (id, text, sender, client, teamMember, team, ownerId, ownerType, created, version, operation, timestamp, auditUser).mapTo[MessageVersion]
    def pk = primaryKey("pk_messageversions", (id, timestamp))
    def idx = index("idx_messageversions", (id, version))
  }

  implicit class MessageExtensions[C[_]](q: Query[Messages, Message, C]) {
    def withUploadedFiles = q
      .joinLeft(UploadedFileDao.uploadedFiles.table)
      .on { case (m, f) =>
        m.id === f.ownerId && f.ownerType === (UploadedFileOwner.Message: UploadedFileOwner)
      }
  }

  val messages: VersionedTableQuery[Message, MessageVersion, Messages, MessageVersions] =
    VersionedTableQuery(TableQuery[Messages], TableQuery[MessageVersions])

}

case class MessageVersion (
  id: UUID,
  text: String,
  sender: MessageSender,
  client: UniversityID,
  teamMember: Option[Usercode],
  team: Option[Team],
  ownerId: UUID,
  ownerType: MessageOwner,
  created: OffsetDateTime,
  version: OffsetDateTime = OffsetDateTime.now(),
  operation: DatabaseOperation,
  timestamp: OffsetDateTime,
  auditUser: Option[Usercode]
) extends StoredVersion[Message]

/**
  * Just the data of a message required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class MessageSave (
  text: String,
  sender: MessageSender,
  teamMember: Option[Usercode]
)

/**
  * Just enough Message to render with.
  */
case class MessageData (
  text: String,
  sender: MessageSender,
  client: UniversityID,
  created: OffsetDateTime,
  teamMember: Option[Usercode],
  team: Option[Team]
)

case class MessageRender(
  message: MessageData,
  files: Seq[UploadedFile]
)

object MessageData {
  def tupled = (apply _).tupled

  // oldest first
  val dateOrdering: Ordering[MessageData] = Ordering.by[MessageData, OffsetDateTime](data => data.created)(JavaTime.dateTimeOrdering)
  val dateOrderingWithFile: Ordering[(MessageData, Option[StoredUploadedFile])] = Ordering.by[(MessageData, Option[StoredUploadedFile]), OffsetDateTime] { case (data, _) => data.created }(JavaTime.dateTimeOrdering)
  val dateOrderingWithFiles: Ordering[(MessageData, Seq[StoredUploadedFile])] = Ordering.by[(MessageData, Seq[StoredUploadedFile]), OffsetDateTime] { case (data, _) => data.created }(JavaTime.dateTimeOrdering)
}

sealed trait MessageSender extends EnumEntry
object MessageSender extends PlayEnum[MessageSender] {
  case object Client extends MessageSender
  case object Team extends MessageSender

  val values: immutable.IndexedSeq[MessageSender] = findValues
}

sealed trait MessageOwner extends EnumEntry
object MessageOwner extends PlayEnum[MessageOwner] {
  case object Enquiry extends MessageOwner
  case object Case extends MessageOwner

  val values: immutable.IndexedSeq[MessageOwner] = findValues
}
