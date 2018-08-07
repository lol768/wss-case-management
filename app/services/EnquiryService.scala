package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{Enquiry, Message, MessageData, MessageOwner}
import domain.dao.{DaoRunner, EnquiryDao, MessageDao}
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

@ImplementedBy(classOf[EnquiryServiceImpl])
trait EnquiryService {
  /**
    * Create initial Enquiry with provided text as a Message.
    */
  def save(enquiry: Enquiry, message: MessageData): Future[Enquiry]

  def getMessagesData(enquiry: UUID): Future[Seq[MessageData]]
}

@Singleton
class EnquiryServiceImpl @Inject() (
  audit: AuditService,
  enquiryDao: EnquiryDao,
  messageDao: MessageDao,
  daoRunner: DaoRunner
)(
  implicit ec: ExecutionContext
) extends EnquiryService {
  override def save(enquiry: Enquiry, message: MessageData): Future[Enquiry] = {
    daoRunner.run((for {
      e <- enquiryDao.insert(enquiry.copy(id = Some(UUID.randomUUID())))
      m <- messageDao.insert(Message(
        id = Some(UUID.randomUUID()),
        text = message.text,
        sender = message.sender,
        ownerId = e.id.get,
        ownerType = MessageOwner.Enquiry
      ))
    } yield e).transactionally)
  }

  def getMessagesData(enquiry: UUID): Future[Seq[MessageData]] = {
    val action = messageDao.getByOwner(enquiry, MessageOwner.Enquiry).result
    // FIXME this gets all the table columns from the DB.
    // can we get Slick to select the subset of columns first?
    daoRunner.run(action).map { messages =>
      messages.map { message =>
        MessageData(
          message.text,
          message.sender,
          message.created
        )
      }
    }
  }
}
