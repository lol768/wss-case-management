package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.Enquiry.EnquirySearchQuery
import domain._
import domain.dao.{DaoRunner, EnquiryDao, MessageDao}
import helpers.JavaTime
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import domain.ExtendedPostgresProfile.api._
import domain.dao.UploadedFileDao.StoredUploadedFile
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnquiryServiceImpl])
trait EnquiryService {
  /**
    * Create initial Enquiry with provided text as a Message.
    */
  def save(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  /**
    * Add a message to an existing Enquiry.
    */
  def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(Message, Seq[UploadedFile])]]

  /**
    * Reassign an enquiry to another team
    */
  def reassign(enquiry: Enquiry, team: Team, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateState(enquiry: Enquiry, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateStateWithMessage(enquiry: Enquiry, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def findEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[EnquiryRender]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Enquiry]]
  def get(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]
  def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Enquiry]]

  def getForRender(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]]
  def getForRender(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]]

  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]
  def findLastViewDate(enquiryID: UUID, usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Option[OffsetDateTime]]]

  def search(query: EnquirySearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]

  def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]

  def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]

  def findEnquiriesNeedingReply(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]
  def findEnquiriesNeedingReply(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]

  def findEnquiriesAwaitingClient(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]
  def findEnquiriesAwaitingClient(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]

  def findClosedEnquiries(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]
  def findClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]

  def countEnquiriesOpenedSince(team: Team, date: OffsetDateTime)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def countEnquiriesClosedSince(team: Team, date: OffsetDateTime)(implicit t: TimingContext): Future[ServiceResult[Int]]
}

@Singleton
class EnquiryServiceImpl @Inject() (
  auditService: AuditService,
  ownerService: OwnerService,
  enquiryDao: EnquiryDao,
  messageDao: MessageDao,
  daoRunner: DaoRunner,
  notificationService: NotificationService,
  uploadedFileService: UploadedFileService
)(implicit ec: ExecutionContext) extends EnquiryService {

  import EnquiryService._

  override def save(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    require(enquiry.id.isEmpty, "Enquiry must not have an existing ID before being saved")
    require(enquiry.key.isEmpty, "Enquiry must not have an existing key before being saved")
    require(message.sender == MessageSender.Client, "Initial message must be from the Client")
    val id = UUID.randomUUID()
    auditService.audit('EnquirySave, id.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(for {
        nextId <- sql"SELECT nextval('SEQ_ENQUIRY_KEY')".as[Int].head
        e <- enquiryDao.insert(enquiry.copy(id = Some(id), key = Some(IssueKey(IssueKeyType.Enquiry, nextId))))
        _ <- addMessageDBIO(e, message, files)
      } yield e).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      enquiry => notificationService.newEnquiry(enquiry).map(_.right.map(_ => enquiry))
    ))
  }

  override def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(Message, Seq[UploadedFile])]] = {
    auditService.audit('EnquiryAddMessage, enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        addMessageDBIO(enquiry, message, files)
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      { case (m, file) => notificationService.enquiryMessage(enquiry, m.sender).map(_.right.map(_ => (m, file))) }
    ))
  }

  private def addMessageDBIO(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)]): DBIO[(Message, Seq[UploadedFile])] =
    for {
      message <- messageDao.insert(Message(
        id = UUID.randomUUID(),
        text = message.text,
        sender = message.sender,
        teamMember = message.teamMember,
        ownerId = enquiry.id.get,
        ownerType = MessageOwner.Enquiry
      ), Seq(enquiry.universityID))
      f <- DBIO.sequence(files.map { case (in, metadata) =>
        uploadedFileService.storeDBIO(in, metadata, message.id, UploadedFileOwner.Message)
      })
    } yield (message, f)

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

  def updateStateWithMessage(enquiry: Enquiry, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Symbol(s"Enquiry${targetState.entryName}WithMessage"), enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(
        addMessageDBIO(enquiry, message, files).andThen(
          enquiryDao.update(enquiry.copy(state = targetState), version)
        )
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      enquiry => notificationService.enquiryMessage(enquiry, message.sender).map(_.right.map(_ => enquiry))
    ))
  }

  override def findEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[EnquiryRender]]] = {
    val query = enquiryDao.findByClientQuery(client).withMessages
      .sortBy {
        case (e, mf) => (e.version.reverse, mf.map { case (m, _) => m.created })
      }
      .map {
        case (e, mf) => (e, mf.map { case (m, f) => (m.messageData, f) })
      }

    // Don't think it's possible within Slick to take a one-to-many mapping
    // and get a collection of (Enquiry, Seq[Message]), so this happens
    // in plain Scala after we've got our (Enquiry, Message) tuples back.

    daoRunner.run(query.result).map { pairs =>
      Right(groupPairs(pairs))
    }
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByIDQuery(id).result.head).map(Right.apply)

  override def get(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    daoRunner.run(enquiryDao.findByIDsQuery(ids.toSet).result).map { enquiries =>
      val lookup = enquiries.groupBy(_.id.get).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Enquiry with ID $id") })
    }

  override def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByKeyQuery(enquiryKey).result.head).map(Right.apply)

  private def getWithMessagesQuery(query: Query[Enquiry.Enquiries, Enquiry, Seq]) =
    query.withMessages.map { case (e, mf) => (e, mf.map { case (m, f) => (m.messageData, f) }) }

  override def getForRender(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit('EnquiryView, id.toString, 'Enquiry, Json.obj()) {
      val query = getWithMessagesQuery(enquiryDao.findByIDQuery(id))

      daoRunner.run(query.result).map { pairs =>
        Right(groupPairs(pairs).head)
      }
    }

  override def getForRender(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit[EnquiryRender]('EnquiryView, (r: EnquiryRender) => r.enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      val query = getWithMessagesQuery(enquiryDao.findByKeyQuery(enquiryKey))

      daoRunner.run(query.result).map { pairs =>
        Right(groupPairs(pairs).head)
      }
    }

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    auditService.findRecentTargetIDsByOperation('EnquiryView, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => get(ids.map(UUID.fromString))
    ))

  override def findLastViewDate(enquiryID: UUID, usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Option[OffsetDateTime]]] =
    auditService.findLastEventDateForTargetID(enquiryID.toString, usercode, 'EnquiryView)

  override def search(query: EnquirySearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    daoRunner.run(enquiryDao.searchQuery(query).take(limit).result).map(Right.apply)

  override def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]] =
    ownerService.getEnquiryOwners(ids)

  override def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] =
    ownerService.setEnquiryOwners(id, owners)

  override def findEnquiriesNeedingReply(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLastSender(enquiryDao.findOpenQuery(team), MessageSender.Client)

  override def findEnquiriesNeedingReply(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLastSender(enquiryDao.findOpenQuery(owner), MessageSender.Client)

  override def findEnquiriesAwaitingClient(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLastSender(enquiryDao.findOpenQuery(team), MessageSender.Team)

  override def findEnquiriesAwaitingClient(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLastSender(enquiryDao.findOpenQuery(owner), MessageSender.Team)

  override def findClosedEnquiries(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLatestMessage(enquiryDao.findClosedQuery(team))

  override def findClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLatestMessage(enquiryDao.findClosedQuery(owner))

  private def findEnquiriesWithLastSender(daoQuery: Query[Enquiry.Enquiries, Enquiry, Seq], lastSender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    val query = daoQuery
      .join(Message.messages.table)
      .on((enquiry, message) => {
        message.id in messageDao.latestForEnquiryQuery(enquiry)
          .filter(_.sender === lastSender)
          .map(_.id)
      })
      .map{ case (enquiry,message) => (enquiry, message.messageData)}

    daoRunner.run(query.result).map { pairs =>
      implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
      Right(pairs.sortBy{ case (enquiry, latestMessage) => Seq(enquiry.version, latestMessage.created).min })
    }
  }

  private def findEnquiriesWithLatestMessage(daoQuery: Query[Enquiry.Enquiries, Enquiry, Seq])(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    val query = daoQuery
      .join(Message.messages.table)
      .on((enquiry, message) => {
        message.id in messageDao.latestForEnquiryQuery(enquiry).map(_.id)
      })
      .map{ case (enquiry,message) => (enquiry, message.messageData)}

    daoRunner.run(query.result).map { pairs =>
      implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
      Right(pairs.sortBy{ case (enquiry, latestMessage) => Seq(enquiry.version, latestMessage.created).min })
    }
  }

  override def countEnquiriesOpenedSince(team: Team, date: OffsetDateTime)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      enquiryDao.findOpenQuery(team)
        .filter(_.created >= date)
        .length.result
    ).map(Right.apply)

  override def countEnquiriesClosedSince(team: Team, date: OffsetDateTime)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      enquiryDao.findClosedQuery(team)
        .filter(_.version >= date)
        .length.result
    ).map(Right.apply)
}

object EnquiryService {
  def groupPairs(pairs: Seq[(Enquiry, Option[(MessageData, Option[StoredUploadedFile])])]): Seq[EnquiryRender] = {
    sortByRecent(
      OneToMany.leftJoin(pairs)(MessageData.dateOrderingWithFile)
        .map { case (enquiry, mf) =>
          EnquiryRender(
            enquiry,
            OneToMany.leftJoin(mf)(StoredUploadedFile.dateOrdering)
              .sorted(MessageData.dateOrderingWithFiles)
              .map { case (m, f) => m -> f.map(_.asUploadedFile) }
          )
        }
    )
  }

  /**
    * Sort by the most recently updated, either by newest message or when the enquiry was last
    * updated (perhaps from its state changing)
    */
  def sortByRecent(data: Seq[EnquiryRender]): Seq[EnquiryRender] = {
    val (open, closed) = data.partition { e => e.enquiry.state != IssueState.Closed }
    open.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse) ++ closed.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse)
  }

  def lastModified(entry: EnquiryRender): OffsetDateTime = {
    import JavaTime.dateTimeOrdering
    Stream.cons(entry.enquiry.version, entry.messages.toStream.map { case (m, _) => m.created }).max
  }
}
