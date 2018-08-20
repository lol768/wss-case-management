package services

import java.time.OffsetDateTime
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

  /**
    * Add a message to an existing Enquiry.
    */
  def addMessage(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Message]]

  def findEnquiriesForClient(client: UniversityID): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]]

  def get(id: UUID): Future[ServiceResult[(Enquiry, Seq[MessageData])]]
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

  import EnquiryService.sortByRecent
  
  override def save(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    val id = UUID.randomUUID()
    val messageId = UUID.randomUUID()
    auditService.audit('EnquirySave, id.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(for {
        e <- enquiryDao.insert(enquiry.copy(id = Some(id)))
        _ <- messageDao.insert(Message(
          id = messageId,
          text = message.text,
          sender = message.sender,
          teamMember = message.teamMember,
          ownerId = e.id.get,
          ownerType = MessageOwner.Enquiry
        ), Seq(enquiry.universityID))
      } yield e).map(Right.apply)
    }
  }

  override def addMessage(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Message]] = {
    val messageId = UUID.randomUUID()
    auditService.audit('EnquiryAddMessage, enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        messageDao.insert(Message(
          id = messageId,
          text = message.text,
          sender = message.sender,
          teamMember = message.teamMember,
          ownerId = enquiry.id.get,
          ownerType = MessageOwner.Enquiry
        ), Seq(enquiry.universityID))
      ).map(Right.apply)
    }
  }

  override def findEnquiriesForClient(client: UniversityID): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]] = {
    val query = for {
      (enquiry, message) <- enquiryDao.findByClientQuery(client)
          .joinLeft(messageDao.findByClientQuery(client))
          .on { (e, m) =>
            e.id === m.ownerId && m.ownerType === (MessageOwner.Enquiry: MessageOwner)
          }
    } yield (enquiry, message.map(_.messageData))

    // Don't think it's possible within Slick to take a one-to-many mapping
    // and get a collection of (Enquiry, Seq[Message]), so this happens
    // in plain Scala after we've got our (Enquiry, Message) tuples back.

    // Newest first
    implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse

    daoRunner.run(query.result).map { pairs =>
      Right(sortByRecent(OneToMany.leftJoin(pairs)))
    }
  }

  override def get(id: UUID): Future[ServiceResult[(Enquiry, Seq[MessageData])]] = {
    val query = for {
      (enquiry, message) <- enquiryDao.findByIDQuery(id).withMessages
    } yield (enquiry, message.map(_.messageData))

    // Newest first
    implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse

    daoRunner.run(query.result).map { pairs =>
      Right(sortByRecent(OneToMany.leftJoin(pairs)).head)
    }
  }
}

object EnquiryService {
  /**
    * Sort by the most recently updated, either by newest message or when the enquiry was last
    * updated (perhaps from its state changing)
    */
  def sortByRecent(data: Seq[(Enquiry, Seq[MessageData])]): Seq[(Enquiry, Seq[MessageData])] = {
    implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
    data.sortBy(lastModified)
  }

  def lastModified(entry: (Enquiry, Seq[MessageData])): OffsetDateTime = {
    import JavaTime.dateTimeOrdering
    entry match {
      case (enquiry, messages) => Stream.cons(enquiry.version, messages.toStream.map(_.created)).max
    }
  }
}
