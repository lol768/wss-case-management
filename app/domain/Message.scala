package domain

import java.time.ZonedDateTime
import java.util.UUID

import enumeratum._
import slick.jdbc.PostgresProfile.api._
import CustomJdbcTypes._

/**
  * Conversational message which can be attached to an Enquiry or Case.
  * Can be either to or from the client.
  */
case class Message (
  id: Option[UUID],
  text: String,
  // TODO store the actual sender, like which team member?
  sender: MessageSender,
  ownerId: UUID,
  ownerType: MessageOwner,
  created: ZonedDateTime = ZonedDateTime.now(),
  version: ZonedDateTime = ZonedDateTime.now()
) extends Versioned[Message] {
  override def atVersion(at: ZonedDateTime): Message = copy(version = at)

  override def storedVersion[B <: StoredVersion[Message]](operation: DatabaseOperation, timestamp: ZonedDateTime): B =
    MessageVersion(
      id.get,
      text,
      sender,
      ownerId,
      ownerType,
      created,
      version,
      operation,
      timestamp
    ).asInstanceOf[B]
}

object Message extends Versioning {

  def tupled = (apply _).tupled

  sealed trait MessageProperties {
    self: Table[_] =>

    def text = column[String]("text")
    def sender = column[MessageSender]("sender")
    def created = column[ZonedDateTime]("created_utc")
    def ownerId = column[UUID]("owner_id")
    def ownerType = column[MessageOwner]("owner_type")
    def version = column[ZonedDateTime]("version")
  }

  class Messages(tag: Tag) extends Table[Message](tag, "message") with VersionedTable[Message] with MessageProperties {
    override def matchesPrimaryKey(other: Message): Rep[Boolean] = id === other.id.orNull

    def id = column[UUID]("id", O.PrimaryKey)

    def * = (id.?, text, sender, ownerId, ownerType, created, version).mapTo[Message]
  }

  class MessageVersions(tag: Tag) extends Table[MessageVersion](tag, "message_version") with StoredVersionTable[Message] with MessageProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[ZonedDateTime]("version_timestamp")

    def * = (id, text, sender, ownerId, ownerType, created, version, operation, timestamp).mapTo[MessageVersion]
    def pk = primaryKey("pk_messageversions", (id, timestamp))
    def idx = index("idx_messageversions", (id, version))
  }

  val messages: VersionedTableQuery[Message, MessageVersion, Messages, MessageVersions] =
    VersionedTableQuery(TableQuery[Messages], TableQuery[MessageVersions])

}

case class MessageVersion (
  id: UUID,
  text: String,
  // TODO store the actual sender, like which team member?
  sender: MessageSender,
  ownerId: UUID,
  ownerType: MessageOwner,
  created: ZonedDateTime,
  version: ZonedDateTime = ZonedDateTime.now(),
  operation: DatabaseOperation,
  timestamp: ZonedDateTime
) extends StoredVersion[Message]

/**
  * Subset of DB data, for two purposes
  *
  * - for passing in for creation where the other columns are defined implicitly
  * - for mapping out to in a query as we don't need these other columns for rendering
  */
case class MessageData (
  text: String,
  sender: MessageSender,
  created: ZonedDateTime
)

sealed trait MessageSender extends EnumEntry
object MessageSender extends Enum[MessageSender] {
  case object Client extends MessageSender
  case object Team extends MessageSender

  val values = findValues
}

sealed trait MessageOwner extends EnumEntry
object MessageOwner extends Enum[MessageOwner] {
  case object Enquiry extends MessageOwner
  case object Case extends MessageOwner

  val values = findValues
}
