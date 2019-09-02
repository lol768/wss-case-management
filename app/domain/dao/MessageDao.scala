package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.MessageDao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[MessageDaoImpl])
trait MessageDao {
  def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message]
  def insert(messageSnippet: StoredMessageSnippet)(implicit ac: AuditLogContext): DBIO[StoredMessageSnippet]
  def update(messageSnippet: StoredMessageSnippet, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredMessageSnippet]
  def delete(messageSnippet: StoredMessageSnippet, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]

  def findSnippetsQuery: Query[MessageSnippets, StoredMessageSnippet, Seq]
}

@Singleton
class MessageDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends MessageDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  override def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message] =
    Message.messages += message

  override def insert(messageSnippet: StoredMessageSnippet)(implicit ac: AuditLogContext): DBIO[StoredMessageSnippet] =
    messageSnippets += messageSnippet

  override def update(messageSnippet: StoredMessageSnippet, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredMessageSnippet] =
    messageSnippets.update(messageSnippet.copy(version = version))

  override def delete(messageSnippet: StoredMessageSnippet, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    messageSnippets.delete(messageSnippet.copy(version = version))

  override def findSnippetsQuery: Query[MessageSnippets, StoredMessageSnippet, Seq] =
    messageSnippets.table
}

object MessageDao {
  // Database entities for Message are under the Message companion object
  
  val messageSnippets: VersionedTableQuery[StoredMessageSnippet, StoredMessageSnippetVersion, MessageSnippets, MessageSnippetVersions] =
    VersionedTableQuery(TableQuery[MessageSnippets], TableQuery[MessageSnippetVersions])

  case class StoredMessageSnippet(
    id: UUID,
    title: String,
    body: String,
    sortOrder: Int,
    version: OffsetDateTime = JavaTime.offsetDateTime,
  ) extends Versioned[StoredMessageSnippet] {
    override def atVersion(at: OffsetDateTime): StoredMessageSnippet = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredMessageSnippet]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredMessageSnippetVersion(
        id,
        title,
        body,
        sortOrder,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]

    def asSnippet(files: Seq[UploadedFile]): MessageSnippet = MessageSnippet(
      id = id,
      title = title,
      body = body,
      files = files,
      lastUpdated = version
    )
  }

  object StoredMessageSnippet {
    def tupled = (apply _).tupled

    implicit val defaultOrdering: Ordering[StoredMessageSnippet] = Ordering.by { s: StoredMessageSnippet => (s.sortOrder, s.title) }
  }

  case class StoredMessageSnippetVersion(
    id: UUID,
    title: String,
    body: String,
    sortOrder: Int,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode],
  ) extends StoredVersion[StoredMessageSnippet]

  trait CommonMessageSnippetProperties { self: Table[_] =>
    def title: Rep[String] = column[String]("title")
    def body: Rep[String] = column[String]("body")
    def sortOrder: Rep[Int] = column[Int]("sort_order")
    def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
  }

  class MessageSnippets(tag: Tag) extends Table[StoredMessageSnippet](tag, "message_snippet")
    with VersionedTable[StoredMessageSnippet]
    with CommonMessageSnippetProperties {

    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

    override def matchesPrimaryKey(other: StoredMessageSnippet): Rep[Boolean] = id === other.id

    override def * : ProvenShape[StoredMessageSnippet] = (id, title, body, sortOrder, version).mapTo[StoredMessageSnippet]
  }

  implicit class MessageSnippetExtensions[C[_]](q: Query[MessageSnippets, StoredMessageSnippet, C]) {
    def withUploadedFiles = q
      .joinLeft(UploadedFileDao.uploadedFiles.table)
      .on { case (s, f) =>
        s.id === f.ownerId && f.ownerType === (UploadedFileOwner.MessageSnippet: UploadedFileOwner)
      }
  }

  class MessageSnippetVersions(tag: Tag) extends Table[StoredMessageSnippetVersion](tag, "message_snippet_version")
    with StoredVersionTable[StoredMessageSnippet]
    with CommonMessageSnippetProperties {

    def id: Rep[UUID] = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredMessageSnippetVersion] =
      (id, title, body, sortOrder, version, operation, timestamp, auditUser).mapTo[StoredMessageSnippetVersion]

    def pk: PrimaryKey = primaryKey("pk_message_snippet_version", (id, timestamp))
    def idx: Index = index("idx_message_snippet_version", (id, version))
  }
}
