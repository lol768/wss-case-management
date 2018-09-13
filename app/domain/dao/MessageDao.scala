package domain.dao

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.Enquiry.Enquiries
import domain.{Message, MessageClient, MessageOwner}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import domain.ExtendedPostgresProfile.api._
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[MessageDaoImpl])
trait MessageDao {
  def insert(message: Message, clients: Seq[UniversityID]): DBIO[Message]

  def findByClientQuery(client: UniversityID): Query[Message.Messages, Message, Seq]

  def latestForEnquiryQuery(enquiry: Enquiries): Query[Message.Messages, Message, Seq]
}

@Singleton
class MessageDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends MessageDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(message: Message, clients: Seq[UniversityID]): DBIO[Message] = for {
    m <- insert(message)
    _ <- addClients(message.id, clients)
  } yield m

  private def insert(message: Message): DBIO[Message] =
    Message.messages += message

  private def addClients(message: UUID, clients: Seq[UniversityID]): DBIO[Option[Int]] =
    Message.messageClients ++= clients.map { uniId =>
      MessageClient(
        UUID.randomUUID(),
        uniId,
        message
      )
    }

  def findByClientQuery(client: UniversityID): Query[Message.Messages, Message, Seq] = {
    val messages = for {
      clients <- Message.messageClients if clients.universityId === client
      message <- clients.message
    } yield message

    // Sorting here might not mean anything one this becomes part of a join
    messages.sortBy(_.created)
  }

  def latestForEnquiryQuery(enquiry: Enquiries): Query[Message.Messages, Message, Seq] = {
    Message.messages.table
      .filter(m => enquiry.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner))
      .sortBy(_.created.reverse)
      .take(1)
  }

}
