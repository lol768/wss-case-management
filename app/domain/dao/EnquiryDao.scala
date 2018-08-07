package domain.dao

import java.util.UUID

import domain.{Enquiry, Message, MessageData}
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[EnquiryDaoImpl])
trait EnquiryDao {
  def insert(enquiry: Enquiry): DBIOAction[Enquiry, NoStream, Effect.Write]
}

@Singleton
class EnquiryDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends EnquiryDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(enquiry: Enquiry): DBIOAction[Enquiry, NoStream, Effect.Write] =
    Enquiry.enquiries += enquiry

  def getById(id: UUID) = Enquiry.enquiries.table.filter(_.id === id)

  def getMessagesById(id: UUID) = Message
}
