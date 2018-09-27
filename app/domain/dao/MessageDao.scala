package domain.dao

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.Enquiry.Enquiries
import domain.ExtendedPostgresProfile.api._
import domain.{Message, MessageOwner}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[MessageDaoImpl])
trait MessageDao {
  def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message]

  // TODO never used in code and doesn't filter by owner
  def findByClientQuery(client: UniversityID): Query[Message.Messages, Message, Seq]

  def latestForEnquiryQuery(enquiry: Enquiries): Query[Message.Messages, Message, Seq]
}

@Singleton
class MessageDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends MessageDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message] =
    Message.messages += message

  override def findByClientQuery(client: UniversityID): Query[Message.Messages, Message, Seq] = {
    // Sorting here might not mean anything one this becomes part of a join
    Message.messages.table.filter(_.client === client).sortBy(_.created)
  }

  override def latestForEnquiryQuery(enquiry: Enquiries): Query[Message.Messages, Message, Seq] = {
    Message.messages.table
      .filter(m => enquiry.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner))
      .sortBy(_.created.reverse)
      .take(1)
  }

}
