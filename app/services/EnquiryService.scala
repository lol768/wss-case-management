package services

import java.time.OffsetDateTime
import java.util.{Date, UUID}

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.CustomJdbcTypes._
import domain.DatabaseOperation.{Delete, Insert, Update}
import domain.ExtendedPostgresProfile.api._
import domain.Pagination._
import domain._
import domain.dao.ClientDao.StoredClient
import domain.dao.EnquiryDao.{Enquiries, EnquiryFilter, EnquirySearchQuery, StoredEnquiry, StoredEnquiryNote}
import domain.dao.MemberDao.StoredMember
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{DaoRunner, EnquiryDao, MemberDao, MessageDao}
import javax.inject.{Inject, Singleton}
import org.quartz.{JobBuilder, Scheduler, TriggerBuilder}
import play.api.Configuration
import play.api.libs.json.Json
import services.EnquiryService._
import services.job.SendEnquiryClientReminderJob
import slick.lifted.Query
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.fileuploads.{UploadedFile, UploadedFileSave}
import warwick.slick.helpers.SlickServiceResults.Implicits._
import warwick.sso.{UniversityID, Usercode}

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
  def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], sendClientReminder: Boolean = true)(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]]

  /**
    * Reassign an enquiry to another team
    */
  def reassign(id: UUID, team: Team, note: EnquiryNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateState(id: UUID, targetState: IssueState, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def updateStateWithMessage(id: UUID, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]]

  def findAllEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[EnquiryRender]]]
  def listEnquiriesForClient(client: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Enquiry]]
  def get(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]
  def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Enquiry]]

  def getForRender(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]]
  def getForRender(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]]
  def getEnquiryHistory(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryHistoryRender]]]

  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]
  def findLastViewDate(enquiryID: UUID, usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Option[OffsetDateTime]]]

  def search(query: EnquirySearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]]

  def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]

  def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]]

  def findEnquiriesNeedingReply(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]]
  def countEnquiriesNeedingReply(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def findEnquiriesAwaitingClient(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]]
  def countEnquiriesAwaitingClient(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def findClosedEnquiries(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]]
  def countClosedEnquiries(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def getOwnersMatching(filter: EnquiryFilter, state: IssueStateFilter)(implicit t: TimingContext): Future[ServiceResult[Seq[Member]]]

  def getLastUpdatedForClients(clients: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Option[OffsetDateTime]]]]

  def getLastUpdatedMessageDate(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[OffsetDateTime]]
  def getLastUpdatedMessageDates(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, OffsetDateTime]]]

  def sendClientReminder(enquiryID: UUID, isFinalReminder: Boolean)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def nextClientReminder(enquiryID: UUID)(implicit t: TimingContext): ServiceResult[Option[OffsetDateTime]]
  def cancelClientReminder(enquiry: Enquiry): Unit
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
  memberService: MemberService,
  scheduler: Scheduler,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends EnquiryService with Logging {

  private lazy val clientRemindersEnabled = configuration.get[Boolean]("wellbeing.features.enquiryClientReminders")

  private def createStoredEnquiry(id: UUID, key: IssueKey, save: EnquirySave) = StoredEnquiry(
    id = id,
    key = key,
    universityID = save.universityID,
    subject = save.subject,
    team = save.team,
    state = save.state
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
    val id = UUID.randomUUID()
    auditService.audit(Operation.Enquiry.Save, id.toString, Target.Enquiry, Json.obj()) {
      clientService.getOrAddClients(Set(enquiry.universityID)).successFlatMapTo(clients =>
        daoRunner.runWithServiceResult(for {
          nextId <- sql"SELECT nextval('SEQ_ENQUIRY_KEY')".as[Int].head
          e <- enquiryDao.insert(createStoredEnquiry(id, IssueKey(IssueKeyType.Enquiry, nextId), enquiry))
          _ <- addMessageDBIO(e.universityID, e.team, id, message, files)
          _ <- notificationService.newEnquiry(e.asEnquiry(clients.head).key, enquiry.team).toDBIO
          _ <-
            if (message.sender == MessageSender.Team)
              notificationService.enquiryMessage(e.asEnquiry(clients.head), message.sender).toDBIO
            else
              DBIO.successful(Done)
          _ <- DBIO.successful {
            val enquiry = e.asEnquiry(clients.head)
            // For if we ever allow creating an enquiry by the team
            message.sender match {
              case MessageSender.Team =>
                scheduleClientReminder(enquiry, isFinalReminder = false)

              case MessageSender.Client =>
                cancelClientReminder(enquiry)
            }
          }
        } yield e.asEnquiry(clients.head))
      )
    }
  }

  override def addMessage(enquiry: Enquiry, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], sendClientReminder: Boolean = true)(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]] = {
    auditService.audit(Operation.Enquiry.AddMessage, enquiry.id.toString, Target.Enquiry, Json.obj()) {
      memberService.getOrAddMember(message.teamMember).successFlatMapTo(member =>
        daoRunner.runWithServiceResult(for {
          (m, f) <- addMessageDBIO(enquiry.client.universityID, enquiry.team, enquiry.id, message, files)
          _ <- notificationService.enquiryMessage(enquiry, m.sender).toDBIO
          _ <- DBIO.successful {
            if (sendClientReminder) {
              message.sender match {
                case MessageSender.Team =>
                  scheduleClientReminder(enquiry, isFinalReminder = message.teamMember.contains(SendEnquiryClientReminderJob.SendMessageAs))

                case MessageSender.Client =>
                  cancelClientReminder(enquiry)
              }
            } else cancelClientReminder(enquiry)
          }
        } yield (m, f)).map(_.map { case (m, f) => (m.asMessageData(member), f) })
      )
    }
  }

  override def reassign(id: UUID, team: Team, note: EnquiryNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] =
    auditService.audit(Operation.Enquiry.Reassign, id.toString, Target.Enquiry, Json.obj("team" -> team.id)) {
      memberService.getOrAddMember(note.teamMember).successFlatMapTo(_ =>
        daoRunner.runWithServiceResult(for {
          (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
          stored <- enquiryDao.update(existing.copy(team = team), version)
          _ <- addNoteDBIO(stored.id, EnquiryNoteType.Referral, note)
          _ <- notificationService.enquiryReassign(stored.asEnquiry(client.asClient)).toDBIO
          _ <- DBIO.successful {
            val enquiry = stored.asEnquiry(client.asClient)
            cancelClientReminder(enquiry)
          }
        } yield stored.asEnquiry(client.asClient))
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
    auditService.audit(Operation.Enquiry.transition(targetState), id.toString, Target.Enquiry, Json.obj()) {
      daoRunner.run(for {
        (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
        stored <- enquiryDao.update(existing.copy(state = targetState), version)
      } yield {
        val enquiry = stored.asEnquiry(client.asClient)

        // Whatever the transition here, cancel any reminder
        cancelClientReminder(enquiry)

        Right(enquiry)
      })
    }
  }

  def updateStateWithMessage(id: UUID, targetState: IssueState, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Enquiry]] = {
    auditService.audit(Operation.Enquiry.transitionWithMessage(targetState), id.toString, Target.Enquiry, Json.obj()) {
      daoRunner.runWithServiceResult(for {
        (existing, client) <- enquiryDao.findByIDQuery(id).withClient.result.head
        stored <- addMessageDBIO(client.universityID, existing.team, existing.id, message, files).andThen(
          enquiryDao.update(existing.copy(state = targetState), version)
        )
        _ <- notificationService.enquiryMessage(stored.asEnquiry(client.asClient), message.sender).toDBIO
        _ <- DBIO.successful {
          val enquiry = stored.asEnquiry(client.asClient)

          // Whatever the transition here, cancel any reminder
          cancelClientReminder(enquiry)
        }
      } yield stored.asEnquiry(client.asClient))
    }
  }

  override def findAllEnquiriesForClient(client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[EnquiryRender]]] = {
    val enquiries = enquiryDao.findByClientQuery(client)
    daoRunner.run(for {
      withClientAndMessages <- enquiries.withClientAndMessages.result
      notes <- enquiryDao.findNotesQuery(withClientAndMessages.map { case (e, _, _) => e.id }.toSet).withMember.result
    } yield (withClientAndMessages, notes)).map { case (withClientAndMessages, notes) =>
      Right(groupTuples(withClientAndMessages, notes))
    }
  }

  override def listEnquiriesForClient(client: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    daoRunner.run(
      enquiryDao.findByClientQuery(client)
        .withLastUpdatedFor(ac.usercode.orNull)
        .sortBy { case (e, _, lu, _, _) => (lu.desc, e.key.desc) }
        .result
    ).map { tuples =>
      Right(tuples.map { case (enquiry, c, lastUpdated, lastMessageFromClient, lastViewed) =>
        EnquiryListRender(enquiry.asEnquiry(c.asClient), lastUpdated, lastMessageFromClient, lastViewed)
      })
    }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByIDQuery(id).withClient.result.head).map { case (e, c) => ServiceResults.success(e.asEnquiry(c.asClient)) }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Enquiry with ID $id")
    }

  override def get(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    daoRunner.run(enquiryDao.findByIDsQuery(ids.toSet).withClient.result)
      .map(_.map { case (e, c) => e.asEnquiry(c.asClient) })
      .map { enquiries =>
        val lookup = enquiries.groupBy(_.id).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Enquiry with ID $id") })
    }

  override def get(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Enquiry]] =
    daoRunner.run(enquiryDao.findByKeyQuery(enquiryKey).withClient.result.head).map { case (e, c) => ServiceResults.success(e.asEnquiry(c.asClient)) }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Enquiry with key ${enquiryKey.string}")
    }

  private def getWithClientAndMessagesAndNotes(query: Query[Enquiries, StoredEnquiry, Seq]) =
    for {
      withClientAndMessages <- query.withClientAndMessages.result
      notes <- enquiryDao.findNotesQuery(withClientAndMessages.map { case (e, _, _) => e.id }.toSet).withMember.result
    } yield (withClientAndMessages, notes)

  override def getForRender(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit(Operation.Enquiry.View, id.toString, Target.Enquiry, Json.obj()) {
      val action = getWithClientAndMessagesAndNotes(enquiryDao.findByIDQuery(id))

      daoRunner.run(action).map { case (withMessages, notes) =>
        Right(groupTuples(withMessages, notes).head)
      }
    }

  override def getForRender(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[EnquiryRender]] =
    auditService.audit[EnquiryRender](Operation.Enquiry.View, (r: EnquiryRender) => r.enquiry.id.toString, Target.Enquiry, Json.obj()) {
      val action = getWithClientAndMessagesAndNotes(enquiryDao.findByKeyQuery(enquiryKey))

      daoRunner.run(action).map { case (withMessages, notes) =>
        Right(groupTuples(withMessages, notes).head)
      }
    }

  override def getEnquiryHistory(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryHistoryRender]]] =
    daoRunner.run(enquiryDao.getEnquiryHistory(id)).flatMap(evs => {
      val usercodes = evs.flatMap(_.auditUser).toSet
      ServiceResults.zip(
        memberService.findMembersIfExists(usercodes),
        clientService.findClientsByUsercodeIfExists(usercodes)
      ).successMapTo { case (members, clients) =>
        evs.map { ev => {
          val fullName = ev.auditUser.flatMap(usercode => members.find(_.usercode == usercode).flatMap(_.fullName))
              .orElse(ev.auditUser.flatMap(usercode => clients.find(_._1 == usercode).flatMap(_._2.fullName)))
              .getOrElse(s"User not found in system")

          EnquiryHistoryRender(
            fullName,
            ev.state,
            ev.operation match {
              case Insert => "Created"
              case Update => "Updated"
              case Delete => "Deleted"
            },
            ev.timestamp
          )
        }}
      }
    })

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    auditService.findRecentTargetIDsByOperation(Operation.Enquiry.View, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => get(ids.map(UUID.fromString))
    ))

  override def findLastViewDate(enquiryID: UUID, usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Option[OffsetDateTime]]] =
    auditService.findLastEventDateForTargetID(enquiryID.toString, usercode, Operation.Enquiry.View)

  override def search(query: EnquirySearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Enquiry]]] =
    daoRunner.run(enquiryDao.searchQuery(query).withClient.take(limit).result).map(_.map { case (e, c) => e.asEnquiry(c.asClient) }).map(Right.apply)

  override def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]] =
    ownerService.getEnquiryOwners(ids)

  override def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]] =
    ownerService.setEnquiryOwners(id, owners)

  override def findEnquiriesNeedingReply(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    findEnquiriesWithLastSender(enquiryDao.findByFilterQuery(filter, IssueStateFilter.Open), MessageSender.Client, listFilter, page)

  override def countEnquiriesNeedingReply(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    countEnquiriesWithLastSender(enquiryDao.findByFilterQuery(filter, IssueStateFilter.Open), MessageSender.Client)

  override def findEnquiriesAwaitingClient(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    findEnquiriesWithLastSender(enquiryDao.findByFilterQuery(filter, IssueStateFilter.Open), MessageSender.Team, listFilter, page)

  override def countEnquiriesAwaitingClient(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    countEnquiriesWithLastSender(enquiryDao.findByFilterQuery(filter, IssueStateFilter.Open), MessageSender.Team)

  override def findClosedEnquiries(filter: EnquiryFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    findEnquiries(enquiryDao.findByFilterQuery(filter, IssueStateFilter.Closed), listFilter, page)

  override def countClosedEnquiries(filter: EnquiryFilter)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      enquiryDao.findByFilterQuery(filter, IssueStateFilter.Closed).length.result
    ).map(Right.apply)

  override def getOwnersMatching(filter: EnquiryFilter, state: IssueStateFilter)(implicit t: TimingContext): Future[ServiceResult[Seq[Member]]] =
    daoRunner.run(
      enquiryDao.findByFilterQuery(filter, state)
        .join(Owner.owners.table)
        .on { case (e, o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry: Owner.EntityType) }
        .join(MemberDao.members.table)
        .on { case ((_, o), m) => o.userId === m.usercode }
        .map { case (_, m) => m }
        .distinct
        .result
    ).map { results => Right(results.map(_.asMember).sorted) }

  private def filterEnquiriesWithLastSender(daoQuery: Query[Enquiries, StoredEnquiry, Seq], lastSender: MessageSender): Query[(Enquiries, StoredClient.Clients, Message.Messages), (StoredEnquiry, StoredClient, Message), Seq] =
    daoQuery
      .withClient
      // group-by join to fetch the ID and sent date of the most recent message for each enquiry
      .join(Message.lastUpdatedEnquiryMessage)
      .on { case ((e, _), (id, _)) => e.id === id }
      // rejoin message on created date to get at sender and filter on it
      .join(Message.messages.table)
      .on { case (((e, _), (_, c)), m) => m.ownerId === e.id && m.created === c }
      .map { case (((e, c), _), m) => (e, c, m)}
      .distinctOn { case (e, _, _) => e.id } // handle the slim possibility of two messages with exactly the same created date
      .filter { case (_, _, m) => m.sender === lastSender }

  private def findEnquiriesWithLastSender(daoQuery: Query[Enquiries, StoredEnquiry, Seq], lastSender: MessageSender, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    daoRunner.run(
      filterEnquiriesWithLastSender(daoQuery, lastSender)
        .map { case (e, c, m) =>
          val mostRecentUpdate = slick.lifted.Case.If(m.created > e.version).Then(m.created).Else(e.version)
          val lastMessageFromClient: Rep[Option[OffsetDateTime]] =
            if (lastSender == MessageSender.Client) m.created.?
            else Option.empty[OffsetDateTime]

          (e, c, mostRecentUpdate, lastMessageFromClient)
        }
        .joinLeft(AuditEvent.latestEventsForUser(Operation.Enquiry.View, ac.usercode.orNull, Target.Enquiry))
        .on { case ((e, _, _, _), (targetId, _)) => e.id.asColumnOf[String] === targetId }
        .map { case ((e, c, lastMessage, lastMessageFromClient), o) => (e, c, lastMessage, lastMessageFromClient, o.flatMap(_._2)) }
        .filter { case (_, _, lastUpdated, lastMessageFromClient, lastViewed) =>
          val lastUpdatedAfterFilter = listFilter.lastUpdatedAfter.fold(true.bind)(lastUpdated > _)
          val lastUpdatedBeforeFilter = listFilter.lastUpdatedBefore.fold(true.bind)(lastUpdated < _)
          val hasUnreadsFilter = listFilter.hasUnreadClientMessages.fold(true.bind.?) {
            case true => lastMessageFromClient.nonEmpty.? && (lastViewed.isEmpty.? || lastViewed < lastMessageFromClient)
            case false => lastMessageFromClient.isEmpty.? || (lastViewed.nonEmpty.? && lastViewed >= lastMessageFromClient)
          }

          lastUpdatedAfterFilter && lastUpdatedBeforeFilter && hasUnreadsFilter
        }
        .sortBy { case (e, _, lu, _, _) => (lu.desc, e.key.desc) }
        .paginate(page)
        .result
    ).map { pairs =>
      Right(pairs.map { case (enquiry, client, lastUpdated, lastMessageFromClient, lastViewed) =>
        EnquiryListRender(enquiry.asEnquiry(client.asClient), lastUpdated, lastMessageFromClient, lastViewed)
      })
    }

  private def countEnquiriesWithLastSender(daoQuery: Query[Enquiries, StoredEnquiry, Seq], lastSender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      filterEnquiriesWithLastSender(daoQuery, lastSender)
        .length
        .result
    ).map(Right.apply)

  private def findEnquiries(daoQuery: Query[Enquiries, StoredEnquiry, Seq], listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[EnquiryListRender]]] =
    daoRunner.run(
      daoQuery
        .withLastUpdatedFor(ac.usercode.orNull)
        .filter { case (_, _, lastUpdated, lastMessageFromClient, lastViewed) =>
          val lastUpdatedAfterFilter = listFilter.lastUpdatedAfter.fold(true.bind)(lastUpdated > _)
          val lastUpdatedBeforeFilter = listFilter.lastUpdatedBefore.fold(true.bind)(lastUpdated < _)
          val hasUnreadsFilter = listFilter.hasUnreadClientMessages.fold(true.bind.?) {
            case true => lastMessageFromClient.nonEmpty.? && (lastViewed.isEmpty.? || lastViewed < lastMessageFromClient)
            case false => lastMessageFromClient.isEmpty.? || (lastViewed.nonEmpty.? && lastViewed >= lastMessageFromClient)
          }

          lastUpdatedAfterFilter && lastUpdatedBeforeFilter && hasUnreadsFilter
        }
        .sortBy { case (e, _, lu, _, _) => (lu.desc, e.key.desc) }
        .paginate(page)
        .result
    ).map { tuples =>
      Right(tuples.map { case (enquiry, client, lastUpdated, lastMessageFromClient, lastViewed) =>
        EnquiryListRender(enquiry.asEnquiry(client.asClient), lastUpdated, lastMessageFromClient, lastViewed)
      })
    }

  override def getLastUpdatedForClients(clients: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Option[OffsetDateTime]]]] =
    daoRunner.run(enquiryDao.getLastUpdatedForClients(clients)).map(r => Right(r.toMap.withDefaultValue(None)))

  override def getLastUpdatedMessageDate(enquiryKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[OffsetDateTime]] =
    daoRunner.run(
      enquiryDao.findByKeyQuery(enquiryKey)
        .join(Message.lastUpdatedEnquiryMessage)
        .on { case (e, (m, _)) => e.id === m }
        .map { case (_, (_, d)) => d }
        .result.head
    ).map(r => Right(r.get))

  override def getLastUpdatedMessageDates(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, OffsetDateTime]]] =
    daoRunner.run(
      enquiryDao.findByIDsQuery(ids)
        .join(Message.lastUpdatedEnquiryMessage)
        .on { case (e, (m, _)) => e.id === m }
        .map { case (e, (_, d)) => (e.id, d) }
        .result
    ).map(r => Right(r.toMap.mapValues(_.get)))

  private def scheduleClientReminder(enquiry: Enquiry, isFinalReminder: Boolean): Unit = {
    if (clientRemindersEnabled) {
      val triggerKey = SendEnquiryClientReminderJob.triggerKey(enquiry.id)

      val triggerTime = JavaTime.offsetDateTime.plus(SendEnquiryClientReminderJob.SendReminderAfter).toInstant

      if (scheduler.checkExists(triggerKey)) {
        scheduler.rescheduleJob(
          triggerKey,
          TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(triggerTime))
            .usingJobData(SendEnquiryClientReminderJob.IsFinalReminderJobDataKey, isFinalReminder)
            .build()
        )
      } else {
        scheduler.scheduleJob(
          JobBuilder.newJob(classOf[SendEnquiryClientReminderJob])
            .withIdentity(SendEnquiryClientReminderJob.jobKey(enquiry.id))
            .usingJobData(SendEnquiryClientReminderJob.EnquiryIDJobDataKey, enquiry.id.toString)
            .build(),
          TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(triggerTime))
            .usingJobData(SendEnquiryClientReminderJob.IsFinalReminderJobDataKey, isFinalReminder)
            .build()
        )
      }
    }
  }

  override def cancelClientReminder(enquiry: Enquiry): Unit = {
    val triggerKey = SendEnquiryClientReminderJob.triggerKey(enquiry.id)

    if (scheduler.checkExists(triggerKey)) {
      scheduler.unscheduleJob(triggerKey)
    }
  }

  override def sendClientReminder(enquiryID: UUID, isFinalReminder: Boolean)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Enquiry.SendClientReminder, enquiryID.toString, Target.Enquiry, Json.obj()) {
      daoRunner.run(getWithClientAndMessagesAndNotes(enquiryDao.findByIDQuery(enquiryID))).flatMap { case (withMessages, notes) =>
        val r = groupTuples(withMessages, notes).head
        val lastMessageFromTeam =
          r.messages
            .filter { m => m.message.sender == MessageSender.Team && !m.message.teamMember.map(_.usercode).contains(SendEnquiryClientReminderJob.SendMessageAs) }
            .map(_.message.created)
            .last

        val message = MessageSave(
          text = views.txt.enquiry.clientReminder(r, lastMessageFromTeam, isFinalReminder).toString().trim,
          sender = MessageSender.Team,
          teamMember = Some(SendEnquiryClientReminderJob.SendMessageAs)
        )

        addMessage(r.enquiry, message, Nil, sendClientReminder = !isFinalReminder).map(_.right.map(_ => Done))
      }
    }

  override def nextClientReminder(enquiryID: UUID)(implicit t: TimingContext): ServiceResult[Option[OffsetDateTime]] = {
    val triggerKey = SendEnquiryClientReminderJob.triggerKey(enquiryID)

    val nextFireTime: Option[OffsetDateTime] =
      if (scheduler.checkExists(triggerKey)) {
        Some(scheduler.getTrigger(triggerKey).getNextFireTime.toInstant.atZone(JavaTime.timeZone).toOffsetDateTime)
      } else {
        None
      }

    Right(nextFireTime)
  }
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
        notesByEnquiry(e.id)
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
