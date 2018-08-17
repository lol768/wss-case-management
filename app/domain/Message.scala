package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import enumeratum._
import slick.jdbc.PostgresProfile.api._
import warwick.sso.{UniversityID, Usercode}

/**
  * Conversational message which can be attached to an Enquiry or Case.
  * Can be either to or from the client.
  */
case class Message (
  id: UUID,
  text: String,
  sender: MessageSender,
  teamMember: Option[Usercode],
  ownerId: UUID,
  ownerType: MessageOwner,
  created: OffsetDateTime = OffsetDateTime.now(),
  version: OffsetDateTime = OffsetDateTime.now()
) extends Versioned[Message] {
  override def atVersion(at: OffsetDateTime): Message = copy(version = at)

  override def storedVersion[B <: StoredVersion[Message]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
    MessageVersion(
      id,
      text,
      sender,
      teamMember,
      ownerId,
      ownerType,
      created,
      version,
      operation,
      timestamp
    ).asInstanceOf[B]
}

/**
  * One-to-many mapping of message to client
  */
private[domain] case class MessageClient (
  id: UUID,
  client: UniversityID,
  message: UUID
)

object Message extends Versioning {

  def tupled = (apply _).tupled

  sealed trait CommonProperties { self: Table[_] =>
    def text = column [String]("text")
    def sender = column[MessageSender]("sender")
    def teamMember = column[Option[Usercode]]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def ownerId = column[UUID]("owner_id")
    def ownerType = column[MessageOwner]("owner_type")
    def version = column[OffsetDateTime]("version")
  }

  class Messages(tag: Tag) extends Table[Message](tag, "message") with VersionedTable[Message] with CommonProperties {
    override def matchesPrimaryKey(other: Message): Rep[Boolean] = id === other.id

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id, text, sender, teamMember, ownerId, ownerType, created, version).mapTo[Message]

    def messageData = (text, sender, created).mapTo[MessageData]
  }

  class MessageVersions(tag: Tag) extends Table[MessageVersion](tag, "message_version") with StoredVersionTable[Message] with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp")

    def * = (id, text, sender, teamMember, ownerId, ownerType, created, version, operation, timestamp).mapTo[MessageVersion]
    def pk = primaryKey("pk_messageversions", (id, timestamp))
    def idx = index("idx_messageversions", (id, version))
  }

  class MessageClients(tag: Tag) extends Table[MessageClient](tag, "message_client") {
    def id = column[UUID]("id")
    def universityId = column[UniversityID]("university_id")
    def messageId = column[UUID]("message_id")

    def * = (id, universityId, messageId).mapTo[MessageClient]

    def message = foreignKey("fk_client_message", messageId, messages.table)(m => m.id)
  }

  val messages: VersionedTableQuery[Message, MessageVersion, Messages, MessageVersions] =
    VersionedTableQuery(TableQuery[Messages], TableQuery[MessageVersions])

  val messageClients = TableQuery[MessageClients]


}

case class MessageVersion (
  id: UUID,
  text: String,
  sender: MessageSender,
  teamMember: Option[Usercode],
  ownerId: UUID,
  ownerType: MessageOwner,
  created: OffsetDateTime,
  version: OffsetDateTime = OffsetDateTime.now(),
  operation: DatabaseOperation,
  timestamp: OffsetDateTime
) extends StoredVersion[Message]

/**
  * Just the data of a message required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class MessageSave (
  text: String
)

/**
  * Just enough Message to render with.
  */
case class MessageData (
  text: String,
  sender: MessageSender,
  created: OffsetDateTime
)

sealed trait MessageSender extends EnumEntry
object MessageSender extends PlayEnum[MessageSender] {
  case object Client extends MessageSender
  case object Team extends MessageSender

  val values = findValues
}

sealed trait MessageOwner extends EnumEntry
object MessageOwner extends PlayEnum[MessageOwner] {
  case object Enquiry extends MessageOwner
  case object Case extends MessageOwner

  val values = findValues
}
