package domain

import java.time.OffsetDateTime
import java.util.UUID

import com.github.tminglei.slickpg.TsVector
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.dao.UploadedFileDao
import domain.dao.UploadedFileDao.StoredUploadedFile
import enumeratum._
import services.AuditLogContext
import warwick.core.helpers.JavaTime
import warwick.fileuploads.{UploadedFile, UploadedFileOwner}
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
  created: OffsetDateTime = JavaTime.offsetDateTime,
  version: OffsetDateTime = JavaTime.offsetDateTime,
) extends Versioned[Message] with Created {
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

  def asMessageData(member: Option[Member]) = MessageData(
    text = text,
    sender = sender,
    client = client,
    created = created,
    teamMember = member,
    team = team
  )
}

object Message extends Versioning {
  def tupled = (apply _).tupled

  case class FormData(
    text: String
  )

  sealed trait CommonProperties { self: Table[_] =>
    def text = column[String]("text")
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
    def searchableText: Rep[TsVector] = column[TsVector]("text_tsv")

    def * = (id, text, sender, client, teamMember, team, ownerId, ownerType, created, version).mapTo[Message]
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

  val lastUpdatedCaseMessage =
    Message.messages.table
      .filter(m => m.ownerType === (MessageOwner.Case: MessageOwner))
      .groupBy(_.ownerId)
      .map { case (id, m) => (id, m.map(_.created).max) }

  val lastUpdatedCaseMessageFromClient =
    Message.messages.table
      .filter(m => m.ownerType === (MessageOwner.Case: MessageOwner) && m.sender === (MessageSender.Client: MessageSender))
      .groupBy(_.ownerId)
      .map { case (id, m) => (id, m.map(_.created).max) }

  val lastUpdatedCasePerClientMessage =
    Message.messages.table
      .filter(m => m.ownerType === (MessageOwner.Case: MessageOwner))
      .groupBy(m => (m.ownerId, m.client))
      .map { case ((id, c), m) => (id, c, m.map(_.created).max) }

  val lastUpdatedEnquiryMessage =
    Message.messages.table
      .filter(m => m.ownerType === (MessageOwner.Enquiry: MessageOwner))
      .groupBy(_.ownerId)
      .map { case (id, m) => (id, m.map(_.created).max) }

  val lastUpdatedEnquiryMessageFromClient =
    Message.messages.table
      .filter(m => m.ownerType === (MessageOwner.Enquiry: MessageOwner) && m.sender === (MessageSender.Client: MessageSender))
      .groupBy(_.ownerId)
      .map { case (id, m) => (id, m.map(_.created).max) }

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
  version: OffsetDateTime = JavaTime.offsetDateTime,
  operation: DatabaseOperation,
  timestamp: OffsetDateTime,
  auditUser: Option[Usercode]
) extends StoredVersion[Message] with Created

/**
  * Just the data of a message required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class MessageSave (
  text: String,
  sender: MessageSender,
  teamMember: Option[Usercode]
) {
  def toMessage(
    client: UniversityID,
    team: Team,
    ownerId: UUID,
    ownerType: MessageOwner
  ): Message = Message(
    id = UUID.randomUUID(),
    text = this.text,
    sender = this.sender,
    client = client,
    teamMember = this.teamMember,
    team = this.teamMember.map(_ => team), // Only store Team if there is an explicit team member
    ownerId = ownerId,
    ownerType = ownerType
  )
}

/**
  * Just enough Message to render with.
  */
case class MessageData (
  text: String,
  sender: MessageSender,
  client: UniversityID,
  created: OffsetDateTime,
  teamMember: Option[Member],
  team: Option[Team]
) extends Created

case class MessageRender(
  message: MessageData,
  files: Seq[UploadedFile]
)

object MessageData {
  def tupled = (apply _).tupled

  // oldest first
  val dateOrdering: Ordering[MessageData] = Ordering.by[MessageData, OffsetDateTime](data => data.created)(JavaTime.dateTimeOrdering)
  val dateOrderingWithFile: Ordering[(MessageData, Option[StoredUploadedFile])] = dateOrdering.on(_._1)
  val dateOrderingWithFiles: Ordering[(MessageData, Seq[StoredUploadedFile])] = dateOrdering.on(_._1)


  def groupOwnerAndMessage[A](messageTuples: Seq[(A, Option[(MessageData, Option[StoredUploadedFile])])]): Seq[(A, Seq[MessageRender])] = {
    OneToMany.leftJoin(messageTuples)(MessageData.dateOrderingWithFile)
      .map { case (a, mf) =>
        a -> groupMessagesAndAttachments(mf)
      }
  }

  def groupMessagesAndAttachments(mf: Seq[(MessageData, Option[StoredUploadedFile])]): Seq[MessageRender] = {
    OneToMany.leftJoin(mf.distinct)(StoredUploadedFile.dateOrdering)
      .sorted(MessageData.dateOrderingWithFiles)
      .map { case (m, f) => MessageRender(m, f.map(_.asUploadedFile)) }
  }
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
