package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{Enquiry, Message, MessageData, MessageOwner}
import domain.dao.{DaoRunner, EnquiryDao, MessageDao}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

@ImplementedBy(classOf[EnquiryServiceImpl])
trait EnquiryService {
  /**
    * Create initial Enquiry with provided text as a Message.
    */
  def save(enquiry: Enquiry, message: MessageData)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def getMessagesData(enquiry: UUID): Future[Seq[MessageData]]
}

@Singleton
class EnquiryServiceImpl @Inject() (
  auditService: AuditService,
  enquiryDao: EnquiryDao,
  messageDao: MessageDao,
  daoRunner: DaoRunner
)(
  implicit ec: ExecutionContext
) extends EnquiryService {
  override def save(enquiry: Enquiry, message: MessageData)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    val id = UUID.randomUUID()
    auditService.audit("EnquirySave", id.toString, "Enquiry", Json.obj()) {
      daoRunner.run((for {
        e <- enquiryDao.insert(enquiry.copy(id = Some(id)))
        m <- messageDao.insert(Message(
          id = Some(UUID.randomUUID()),
          text = message.text,
          sender = message.sender,
          ownerId = e.id.get,
          ownerType = MessageOwner.Enquiry
        ))
      } yield e).transactionally).map(Right.apply)
    }
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
