package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.MessageSender.Client
import domain._
import domain.dao.{DaoRunner, EnquiryDao, MessageDao}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import slick.jdbc.PostgresProfile.api._
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

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

  /**
    * Reassign an enquiry to another team
    */
  def reassign(enquiry: Enquiry, team: Team, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateState(enquiry: Enquiry, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateStateWithMessage(enquiry: Enquiry, targetState: IssueState, message: MessageSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def findEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[(Enquiry, Seq[MessageData])]]
  def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[(Enquiry, Seq[MessageData])]]

  def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]

  def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]

  def findEnquiriesNeedingReply(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]

  def findEnquiriesNeedingReply(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]
}

@Singleton
class EnquiryServiceImpl @Inject() (
  auditService: AuditService,
  ownerService: OwnerService,
  enquiryDao: EnquiryDao,
  messageDao: MessageDao,
  daoRunner: DaoRunner,
  notificationService: NotificationService
)(implicit ec: ExecutionContext) extends EnquiryService {

  import EnquiryService._
  
  override def save(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    require(enquiry.id.isEmpty, "Enquiry must not have an existing ID before being saved")
    require(enquiry.key.isEmpty, "Enquiry must not have an existing key before being saved")
    require(message.sender == MessageSender.Client, "Initial message must be from the Client")
    val id = UUID.randomUUID()
    val messageId = UUID.randomUUID()
    auditService.audit('EnquirySave, id.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(for {
        nextId <- sql"SELECT nextval('SEQ_ENQUIRY_KEY')".as[Int].head
        e <- enquiryDao.insert(enquiry.copy(id = Some(id), key = Some(IssueKey(IssueKeyType.Enquiry, nextId))))
        _ <- messageDao.insert(Message(
          id = messageId,
          text = message.text,
          sender = message.sender,
          teamMember = message.teamMember,
          ownerId = e.id.get,
          ownerType = MessageOwner.Enquiry
        ), Seq(enquiry.universityID))
      } yield e).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      enquiry => notificationService.newEnquiry(enquiry).map(_.right.map(_ => enquiry))
    ))
  }

  override def addMessage(enquiry: Enquiry, message: MessageSave)(implicit ac: AuditLogContext): Future[ServiceResult[Message]] = {
    auditService.audit('EnquiryAddMessage, enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        addMessageDBIO(enquiry, message)
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      message => notificationService.enquiryMessage(enquiry, message.sender).map(_.right.map(_ => message))
    ))
  }


  override def reassign(enquiry: Enquiry, team: Team, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] =
    auditService.audit('EnquiryReassign, enquiry.id.get.toString, 'Enquiry, Json.obj("team" -> team.id)) {
      daoRunner.run(
        enquiryDao.update(enquiry.copy(team = team), version)
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      enquiry => notificationService.enquiryReassign(enquiry).map(_.right.map(_ => enquiry))
    ))

  override def updateState(enquiry: Enquiry, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Symbol(s"Enquiry${targetState.entryName}"), enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        enquiryDao.update(enquiry.copy(state = targetState), version)
      ).map(Right.apply)
    }
  }

  def updateStateWithMessage(enquiry: Enquiry, targetState: IssueState, message: MessageSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Symbol(s"Enquiry${targetState.entryName}WithMessage"), enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        addMessageDBIO(enquiry, message).andThen(
          enquiryDao.update(enquiry.copy(state = targetState), version)
        )
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      enquiry => notificationService.enquiryMessage(enquiry, message.sender).map(_.right.map(_ => enquiry))
    ))
  }

  override def findEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, Seq[MessageData])]]] = {
    val query = enquiryDao.findByClientQuery(client).withMessages
      .sortBy {
        case (e, m) => (e.version.reverse, m.map(_.created))
      }
      .map {
        case (e, m) => (e, m.map(_.messageData))
      }

    // Don't think it's possible within Slick to take a one-to-many mapping
    // and get a collection of (Enquiry, Seq[Message]), so this happens
    // in plain Scala after we've got our (Enquiry, Message) tuples back.

    daoRunner.run(query.result).map { pairs =>
      Right(groupPairs(pairs))
    }
  }

  private def getWithMessagesQuery(query: Query[Enquiry.Enquiries, Enquiry, Seq]) =
    query.withMessages.map { case (e, m) => (e, m.map(_.messageData)) }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[(Enquiry, Seq[MessageData])]] = {
    val query = getWithMessagesQuery(enquiryDao.findByIDQuery(id))

    daoRunner.run(query.result).map { pairs =>
      Right(groupPairs(pairs).head)
    }
  }

  override def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[(Enquiry, Seq[MessageData])]] = {
    val query = getWithMessagesQuery(enquiryDao.findByKeyQuery(enquiryKey))

    daoRunner.run(query.result).map { pairs =>
      Right(groupPairs(pairs).head)
    }
  }

  override def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]] =
    ownerService.getEnquiryOwners(ids)

  override def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] =
    ownerService.setEnquiryOwners(id, owners)

  override def findEnquiriesNeedingReply(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    findEnquiriesNeedingReplyInternal(enquiryDao.findOpenQuery(team))
  }

  override def findEnquiriesNeedingReply(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    findEnquiriesNeedingReplyInternal(enquiryDao.findOpenQuery(owner))
  }

  private def findEnquiriesNeedingReplyInternal(daoQuery: Query[Enquiry.Enquiries, Enquiry, Seq])(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    val query = daoQuery
      .join(Message.messages.table)
      .on((enquiry, message) => {
        message.id in messageDao.latestForEnquiryQuery(enquiry)
          .filter(_.sender === (Client : MessageSender))
          .map(_.id)
      })
      .map{ case (enquiry,message) => (enquiry, message.messageData)}

    daoRunner.run(query.result).map { pairs =>
      implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
      Right(pairs.sortBy{ case (enquiry, latestMessage) => Seq(enquiry.version, latestMessage.created).min })
    }
  }

  private def addMessageDBIO(enquiry: Enquiry, message: MessageSave) = {
    messageDao.insert(Message(
      id = UUID.randomUUID(),
      text = message.text,
      sender = message.sender,
      teamMember = message.teamMember,
      ownerId = enquiry.id.get,
      ownerType = MessageOwner.Enquiry
    ), Seq(enquiry.universityID))
  }
}

object EnquiryService {
  def groupPairs(pairs: Seq[(Enquiry, Option[MessageData])]): Seq[(Enquiry, Seq[MessageData])] = {
    sortByRecent(OneToMany.leftJoin(pairs)(MessageData.dateOrdering))
  }

  /**
    * Sort by the most recently updated, either by newest message or when the enquiry was last
    * updated (perhaps from its state changing)
    */
  def sortByRecent(data: Seq[(Enquiry, Seq[MessageData])]): Seq[(Enquiry, Seq[MessageData])] = {
    val (open, closed) = data.partition { case (e, _) => e.state != IssueState.Closed }
    open.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse) ++ closed.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse)
  }

  def lastModified(entry: (Enquiry, Seq[MessageData])): OffsetDateTime = {
    import JavaTime.dateTimeOrdering
    val (enquiry, messages) = entry
    Stream.cons(enquiry.version, messages.toStream.map(_.created)).max
  }
}
