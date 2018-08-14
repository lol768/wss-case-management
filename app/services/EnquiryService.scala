package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.{DaoRunner, EnquiryDao, MessageDao}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnquiryServiceImpl])
trait EnquiryService {
  /**
    * Create initial Enquiry with provided text as a Message.
    */
  def save(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def findEnquiriesForClient(client: UniversityID): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]]
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
  override def save(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    val id = UUID.randomUUID()
    val messageId = UUID.randomUUID()
    auditService.audit("EnquirySave", id.toString, "Enquiry", Json.obj()) {
      daoRunner.run(for {
        e <- enquiryDao.insert(enquiry.copy(id = Some(id)))
        _ <- messageDao.insert(Message(
          id = messageId,
          text = message.text,
          sender = MessageSender.Client,
          teamMember = None,
          ownerId = e.id.get,
          ownerType = MessageOwner.Enquiry
        ), Seq(enquiry.universityID))
      } yield e).map(Right.apply)
    }
  }

  override def findEnquiriesForClient(client: UniversityID): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]] = {
    val query = for {
      (enquiry, message) <- enquiryDao.findByClient(client)
          .joinLeft(messageDao.getByClient(client))
          .on { (e, m) =>
            e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry:MessageOwner)
          }
    } yield (enquiry, message.map(_.messageData))

    // Not sure if it's possible within Slick to take a one-to-many mapping
    // and get a collection of (Enquiry, Seq[Message]), so this happens
    // in plain Scala after we've got our (Enquiry, Message) tuples back.

    // Newest first
    implicit def dateOrdering = JavaTime.dateTimeOrdering.reverse

    daoRunner.run(query.result).map { pairs =>
      Right(OneToMany.leftJoin(pairs).sortBy {
        case (enquiry, _) => enquiry.version
      })
    }
  }

}
