package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.EnquiryDao.{Enquiries, EnquirySearchQuery, StoredEnquiry, StoredEnquiryNote}
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{DaoRunner, EnquiryDao, MemberDao, MessageDao}
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.{JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}
import ServiceResults.Implicits._
import domain.dao.ClientDao.StoredClient
import domain.dao.MemberDao.StoredMember
import QueryHelpers._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnquiryServiceImpl])
trait EnquiryService {
  /**
    * Create initial Enquiry with provided text as a Message.
    */
  def save(enquiry: EnquirySave, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  /**
    * Add a message to an existing Enquiry.
    */
  def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]]

  /**
    * Reassign an enquiry to another team
    */
  def reassign(id: UUID, team: Team, note: EnquiryNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateState(id: UUID, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateStateWithMessage(id: UUID, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

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
  def countClosedEnquiries(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]]
  def countClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]

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
  uploadedFileService: UploadedFileService,
  clientService: ClientService,
  memberService: MemberService
)(implicit ec: ExecutionContext) extends EnquiryService {

  import EnquiryService._

  private def createStoredEnquiry(id: UUID, key: IssueKey, save: EnquirySave) = StoredEnquiry(
    id = id,
    key = key,
    universityID = save.universityID,
    subject = save.subject,
    team = save.team
  )

  private def addMessageDBIO(client: UniversityID, team: Team, enquiryId: UUID, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): DBIO[(Message, Seq[UploadedFile])] =
    for {
      message <- messageDao.insert(message.toMessage(
        client = client,
        team = team, // Only store Team if there is an explicit team member
        ownerId = enquiryId,
        ownerType = MessageOwner.Enquiry
      ))
      f <- DBIO.sequence(files.map { case (in, metadata) =>
        uploadedFileService.storeDBIO(in, metadata, ac.usercode.get, message.id, UploadedFileOwner.Message)
      })
    } yield (message, f)

  override def save(enquiry: EnquirySave, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    require(message.sender == MessageSender.Client, "Initial message must be from the Client")
    val id = UUID.randomUUID()
    auditService.audit('EnquirySave, id.toString, 'Enquiry, Json.obj()) {
      clientService.getOrAddClients(Set(enquiry.universityID)).successFlatMapTo(clients =>
        daoRunner.run(for {
          nextId <- sql"SELECT nextval('SEQ_ENQUIRY_KEY')".as[Int].head
          e <- enquiryDao.insert(createStoredEnquiry(id, IssueKey(IssueKeyType.Enquiry, nextId), enquiry))
          _ <- addMessageDBIO(e.universityID, e.team, id, message, files)
        } yield e).flatMap(enquiry =>
          notificationService.newEnquiry(enquiry.key).map(_.right.map(_ => enquiry.asEnquiry(clients.head)))
        )
      )
    }
  }

  override def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]] = {
    auditService.audit('EnquiryAddMessage, enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      memberService.getOrAddMember(message.teamMember).successFlatMapTo(member =>
        daoRunner.run(for {
          (m, f) <- addMessageDBIO(enquiry.client.universityID, enquiry.team, enquiry.id.get, message, files)
        } yield (m, f)).flatMap { case (m, f) =>
          notificationService.enquiryMessage(enquiry, m.sender).map(_.map(_ =>
            (m.asMessageData(member), f)
          ))
        }
      )
    }
  }

  override def reassign(id: UUID, team: Team, note: EnquiryNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] =
    auditService.audit('EnquiryReassign, id.toString, 'Enquiry, Json.obj("team" -> team.id)) {
      memberService.getOrAddMember(note.teamMember).successFlatMapTo(_ =>
        daoRunner.run(for {
          (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
          stored <- enquiryDao.update(existing.copy(team = team), version)
          _ <- addNoteDBIO(stored.id, EnquiryNoteType.Referral, note)
        } yield (stored, client)).flatMap { case (stored, client) =>
          val enquiry = stored.asEnquiry(client.asClient)
          notificationService.enquiryReassign(enquiry).map(_.map(_ => enquiry))
        }
      )
    }

  private def addNoteDBIO(enquiryID: UUID, noteType: EnquiryNoteType, note: EnquiryNoteSave)(implicit ac: AuditLogContext): DBIO[StoredEnquiryNote] =
    enquiryDao.insertNote(
      StoredEnquiryNote(
        id = UUID.randomUUID(),
        enquiryID = enquiryID,
        noteType = noteType,
        text = note.text,
        teamMember = note.teamMember,
        created = JavaTime.offsetDateTime,
        version = JavaTime.offsetDateTime
      )
    )

  override def updateState(id: UUID, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Symbol(s"Enquiry${targetState.entryName}"), id.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(for {
        (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
        stored <- enquiryDao.update(existing.copy(state = targetState), version)
      } yield {
        Right(stored.asEnquiry(client.asClient))
      })
    }
  }

  def updateStateWithMessage(id: UUID, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Symbol(s"Enquiry${targetState.entryName}WithMessage"), id.toString, 'Enquiry, Json.obj()) {
      daoRunner.run(for {
        (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
        stored <- addMessageDBIO(client.universityID, existing.team, existing.id, message, files).andThen(
          enquiryDao.update(existing.copy(state = targetState), version)
        )
      } yield (stored, client)).flatMap { case (stored, client) =>
        val enquiry = stored.asEnquiry(client.asClient)
        notificationService.enquiryMessage(enquiry, message.sender).map(_.map(_ => enquiry))
      }
    }
  }

  override def findEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[EnquiryRender]]] = {
    val enquiries = enquiryDao.findByClientQuery(client)
    daoRunner.run(for {
      withClientAndMessages <- enquiries.withClientAndMessages.result
      notes <- enquiryDao.findNotesQuery(withClientAndMessages.map { case (e, _, _) => e.id }.toSet).withMember.result
    } yield (withClientAndMessages, notes)).map { case (withClientAndMessages, notes) =>
      Right(groupTuples(withClientAndMessages, notes))
    }
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByIDQuery(id).withClient.result.head).map { case (e, c) => Right(e.asEnquiry(c.asClient)) }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Enquiry with ID $id")
    }

  override def get(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    daoRunner.run(enquiryDao.findByIDsQuery(ids.toSet).withClient.result)
      .map(_.map { case (e, c) => e.asEnquiry(c.asClient) })
      .map { enquiries =>
        val lookup = enquiries.groupBy(_.id.get).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Enquiry with ID $id") })
    }

  override def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByKeyQuery(enquiryKey).withClient.result.head).map { case (e, c) => Right(e.asEnquiry(c.asClient)) }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Enquiry with key ${enquiryKey.string}")
    }

  private def getWithClientAndMessagesAndNotes(query: Query[Enquiries, StoredEnquiry, Seq]) =
    for {
      withClientAndMessages <- query.withClientAndMessages.result
      notes <- enquiryDao.findNotesQuery(withClientAndMessages.map { case (e, _, _) => e.id }.toSet).withMember.result
    } yield (withClientAndMessages, notes)

  override def getForRender(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit('EnquiryView, id.toString, 'Enquiry, Json.obj()) {
      val action = getWithClientAndMessagesAndNotes(enquiryDao.findByIDQuery(id))

      daoRunner.run(action).map { case (withMessages, notes) =>
        Right(groupTuples(withMessages, notes).head)
      }
    }

  override def getForRender(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit[EnquiryRender]('EnquiryView, (r: EnquiryRender) => r.enquiry.id.get.toString, 'Enquiry, Json.obj()) {
      val action = getWithClientAndMessagesAndNotes(enquiryDao.findByKeyQuery(enquiryKey))

      daoRunner.run(action).map { case (withMessages, notes) =>
        Right(groupTuples(withMessages, notes).head)
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
    daoRunner.run(enquiryDao.searchQuery(query).withClient.take(limit).result).map(_.map { case (e, c) => e.asEnquiry(c.asClient) }).map(Right.apply)

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

  override def countClosedEnquiries(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      enquiryDao.findClosedQuery(team).length.result
    ).map(Right.apply)

  override def findClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] =
    findEnquiriesWithLatestMessage(enquiryDao.findClosedQuery(owner))

  override def countClosedEnquiries(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      enquiryDao.findClosedQuery(owner).length.result
    ).map(Right.apply)

  private def findEnquiriesWithLastSender(daoQuery: Query[Enquiries, StoredEnquiry, Seq], lastSender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    val query = daoQuery
      .withClient
      .join(Message.messages.table)
      .on { case ((enquiry, _), message) =>
        message.id in messageDao.latestForEnquiryQuery(enquiry)
          .filter(_.sender === lastSender)
          .map(_.id)
      }
      .flattenJoin
      .joinLeft(MemberDao.members.table)
      .on { case ((_, _, m), member) => m.teamMember.map(_ === member.usercode) }
      .map { case ((enquiry, client, message), member) => (enquiry, client, message, member)}

    daoRunner.run(query.result).map { pairs =>
      implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
      Right(
        pairs.map { case (enquiry, client, latestMessage, member) => (enquiry.asEnquiry(client.asClient), latestMessage.asMessageData(member.map(_.asMember))) }
          .sortBy{ case (enquiry, latestMessage) => Seq(enquiry.lastUpdated, latestMessage.created).min }
      )
    }
  }

  private def findEnquiriesWithLatestMessage(daoQuery: Query[Enquiries, StoredEnquiry, Seq])(implicit t: TimingContext): Future[ServiceResult[Seq[(Enquiry, MessageData)]]] = {
    val query = daoQuery
      .withClient
      .join(Message.messages.table)
      .on { case ((enquiry, _), message) =>
        message.id in messageDao.latestForEnquiryQuery(enquiry).map(_.id)
      }
      .flattenJoin
      .joinLeft(MemberDao.members.table)
      .on { case ((_, _, m), member) => m.teamMember.map(_ === member.usercode) }
      .map { case ((enquiry, client, message), member) => (enquiry, client, message, member)}

    daoRunner.run(query.result).map { pairs =>
      implicit def dateOrdering: Ordering[OffsetDateTime] = JavaTime.dateTimeOrdering.reverse
      Right(
        pairs.map { case (enquiry, client, latestMessage, member) => (enquiry.asEnquiry(client.asClient), latestMessage.asMessageData(member.map(_.asMember))) }
          .sortBy{ case (enquiry, latestMessage) => Seq(enquiry.lastUpdated, latestMessage.created).min }
      )
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
  def groupTuples(messageTuples: Seq[(StoredEnquiry, StoredClient, Option[(Message, Option[StoredUploadedFile], Option[StoredMember])])], notes: Seq[(StoredEnquiryNote, StoredMember)]): Seq[EnquiryRender] = {
    val enquiriesAndMessages = MessageData.groupOwnerAndMessage(
      messageTuples.map { case (e, c, m) => (
        e.asEnquiry(c.asClient),
        m.map { case (msg, f, member) => (msg.asMessageData(member.map(_.asMember)), f) }
      ) }
    )

    val notesByEnquiry = notes
      .groupBy { case (n, _) => n.enquiryID }
      .mapValues(_.map { case (n, m) => n.asEnquiryNote(m.asMember) }.sorted(EnquiryNote.dateOrdering))
      .withDefaultValue(Seq())

    sortByRecent(enquiriesAndMessages.map { case (e, m) =>
      EnquiryRender(
        e,
        m,
        notesByEnquiry(e.id.get)
      )
    })
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
    Stream.cons(entry.enquiry.lastUpdated, entry.messages.toStream.map(_.message.created)).max
  }
}
