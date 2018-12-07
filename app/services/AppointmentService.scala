package services

import java.time.OffsetDateTime
import java.util.{Date, UUID}

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.dao.AppointmentDao.{AppointmentCase, AppointmentSearchQuery, Appointments, StoredAppointment, StoredAppointmentClient}
import domain.dao.CaseDao.StoredCase
import domain.dao.ClientDao.StoredClient
import domain.dao.{AppointmentDao, DaoRunner}
import domain.{Case, _}
import helpers.ServiceResults
import helpers.ServiceResults.Implicits._
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Provider, Singleton}
import org.quartz._
import play.api.Configuration
import play.api.libs.json.Json
import services.AppointmentService._
import services.job.SendAppointmentClientReminderJob
import services.office365.Office365CalendarService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[AppointmentServiceImpl])
trait AppointmentService {
  def create(appointment: AppointmentSave, clients: Set[UniversityID], teamMembers: Set[Usercode], team: Team, caseIDs: Set[UUID], currentUser: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def update(id: UUID, appointment: AppointmentSave, cases: Set[UUID], clients: Set[UniversityID], teamMembers: Set[Usercode], currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def reschedule(id: UUID, appointment: AppointmentSave, currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]

  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Appointment]]
  def findFull(id: UUID)(implicit t: TimingContext): Future[ServiceResult[AppointmentRender]]
  def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]
  def findFull(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def find(appointmentKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Appointment]]
  def findForRender(appointmentKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[AppointmentRender]]
  def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findForCase(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]

  /**
    * Appointments for the given client in the future that are not cancelled
    */
  def countForClientBadge(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def search(query: AppointmentSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]
  def findForSearch(query: AppointmentSearchQuery)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countForSearch(query: AppointmentSearchQuery)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def findDeclinedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findDeclinedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findProvisionalAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findProvisionalAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findAppointmentsNeedingOutcome(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findAppointmentsNeedingOutcome(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findAcceptedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAcceptedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findAcceptedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAcceptedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentClient]]]
  def getTeamMembers(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentTeamMember]]]

  def clientAccept(appointmentID: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def clientDecline(appointmentID: UUID, universityID: UniversityID, reason: AppointmentCancellationReason)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]

  def recordOutcomes(appointmentID: UUID, clientAttendance: Map[UniversityID, (AppointmentClientAttendanceState, Option[AppointmentCancellationReason])], outcomes: AppointmentOutcomesSave, note: Option[CaseNoteSave], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def cancel(appointmentID: UUID, reason: AppointmentCancellationReason, note: Option[CaseNoteSave], currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]

  def sendClientReminder(appointmentID: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class AppointmentServiceImpl @Inject()(
  auditService: AuditService,
  notificationService: NotificationService,
  clientService: ClientService,
  memberService: MemberService,
  ownerService: OwnerService,
  office365CalendarService: Office365CalendarService,
  caseServiceProvider: Provider[CaseService],
  daoRunner: DaoRunner,
  dao: AppointmentDao,
  scheduler: Scheduler,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends AppointmentService with Logging {

  private lazy val clientRemindersEnabled = configuration.get[Boolean]("wellbeing.features.appointmentClientReminders")
  private lazy val caseService = caseServiceProvider.get()

  private def createStoredAppointment(id: UUID, key: IssueKey, save: AppointmentSave, team: Team): StoredAppointment =
    StoredAppointment(
      id,
      key,
      save.start,
      save.duration,
      save.roomID,
      team,
      save.appointmentType,
      save.purpose,
      AppointmentState.Provisional,
      None,
      List(),
      None,
      List(),
      None,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime
    )

  private def createCaseLink(appointment:UUID, caseId: UUID, teamMember:Usercode) =
    AppointmentCase(
      UUID.randomUUID(),
      appointment,
      caseId,
      teamMember,
      JavaTime.offsetDateTime
    )

  override def create(appointment: AppointmentSave, clients: Set[UniversityID], teamMembers: Set[Usercode], team: Team, caseIDs: Set[UUID], currentUser: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] = {
    val id = UUID.randomUUID()
    auditService.audit('AppointmentSave, id.toString, 'Appointment, Json.obj()) {
      ServiceResults.zip(
        clientService.getOrAddClients(clients),
        memberService.getOrAddMembers(teamMembers)
      ).successFlatMapTo { _ =>
        daoRunner.run(for {
          nextId <- sql"SELECT nextval('SEQ_APPOINTMENT_KEY')".as[Int].head
          inserted <- dao.insert(createStoredAppointment(id, IssueKey(IssueKeyType.Appointment, nextId), appointment, team))
          _ <- dao.insertCaseLinks(caseIDs.map(cid => createCaseLink(id, cid, ac.usercode.getOrElse(teamMembers.head))))
          _ <- dao.insertClients(clients.map { universityID =>
            StoredAppointmentClient(
              universityID,
              inserted.id,
              AppointmentState.Provisional,
              None,
              None,
              JavaTime.offsetDateTime,
              JavaTime.offsetDateTime
            )
          })
        } yield inserted).flatMap { a =>
          ownerService.setAppointmentOwners(a.id, teamMembers).successFlatMapTo { setOwnersResult =>
            val appointment = a.asAppointment
            office365CalendarService.updateAppointment(appointment.id, setOwnersResult)
            val ownersToNotify = setOwnersResult.all.map(_.userId).toSet - currentUser
            ServiceResults.zip(
              notificationService.ownerNewAppointment(ownersToNotify, appointment).map(sr =>
                ServiceResults.logErrors(sr, logger, ())
              ),
              notificationService.clientNewAppointment(clients).map(sr =>
                ServiceResults.logErrors(sr, logger, ())
              )
            ).map(_ => {
              scheduleClientReminder(appointment)
              Right(appointment)
            })
          }
        }
      }
    }
  }

  override def update(id: UUID, changes: AppointmentSave, cases: Set[UUID], clients: Set[UniversityID], teamMembers: Set[Usercode], currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] = {
    auditService.audit('AppointmentUpdate, id.toString, 'Appointment, Json.obj()) {
      def newState(existing: StoredAppointment, clientResult: UpdateDifferencesResult[StoredAppointmentClient]): AppointmentState =
        if (existing.state == AppointmentState.Provisional || existing.state == AppointmentState.Cancelled || existing.state == AppointmentState.Attended)
          existing.state
        else if (clientResult.all.exists(_.state == AppointmentState.Accepted))
          // At least one client has still accepted
          AppointmentState.Accepted
        else
          // All accepted clients have been removed; back to provisional
          AppointmentState.Provisional

      ServiceResults.zip(
        clientService.getOrAddClients(clients),
        memberService.getOrAddMembers(teamMembers)
      ).successFlatMapTo { _ =>
        daoRunner.run(for {
          existing <- dao.findByIDQuery(id).result.head
          _ <- updateDifferencesDBIO[AppointmentCase, UUID](
            cases,
            dao.casesForAppointmentQuery(id),
            _.id,
            caseId => createCaseLink(id, caseId, ac.usercode.getOrElse(teamMembers.head)),
            dao.insertCaseLinks,
            dao.deleteCaseLinks
          )
          clientsResult <- updateDifferencesDBIO[StoredAppointmentClient, UniversityID](
            clients,
            dao.findClientsQuery(Set(id)),
            _.universityID,
            universityID => StoredAppointmentClient(
              universityID,
              id,
              AppointmentState.Provisional,
              None,
              None,
              JavaTime.offsetDateTime,
              JavaTime.offsetDateTime
            ),
            dao.insertClients,
            dao.deleteClients
          )
          updated <- dao.update(
            // We re-construct the whole StoredAppointment here so that missing a value will throw a compile error
            StoredAppointment(
              existing.id,
              existing.key,
              existing.start, // Not allowed for updates, use reschedule()
              existing.duration, // Not allowed for updates, use reschedule()
              existing.roomID, // Not allowed for updates, use reschedule()
              existing.team,
              changes.appointmentType,
              changes.purpose,
              newState(existing, clientsResult),
              if (newState(existing, clientsResult) == AppointmentState.Provisional)
                None
              else
                existing.cancellationReason,
              existing.outcome,
              existing.dsaSupportAccessed,
              existing.dsaActionPoints,
              existing.dsaActionPointOther,
              existing.created,
              JavaTime.offsetDateTime
            ),
            version
          )
        } yield (updated, clientsResult)).flatMap { case (a, clientsResult) =>
          ownerService.setAppointmentOwners(a.id, teamMembers).successFlatMapTo { setOwnersResult =>
            office365CalendarService.updateAppointment(a.id, setOwnersResult)
            val appointment = a.asAppointment

            val notifyAddedClients: Future[ServiceResult[Option[Activity]]] =
              if (clientsResult.added.nonEmpty)
                notificationService.clientNewAppointment(clientsResult.added.map(_.universityID).toSet).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            val addedOwnersToNotify = setOwnersResult.added.map(_.userId).toSet - currentUser
            val notifyAddedOwners: Future[ServiceResult[Option[Activity]]] =
              if (addedOwnersToNotify.nonEmpty)
                notificationService.ownerNewAppointment(addedOwnersToNotify, appointment).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            val notifyRemovedClients: Future[ServiceResult[Option[Activity]]] =
              if (clientsResult.removed.nonEmpty)
                notificationService.clientCancelledAppointment(clientsResult.removed.map(_.universityID).toSet).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            val removedOwnersToNotify = setOwnersResult.removed.map(_.userId).toSet - currentUser
            val notifyRemovedOwners: Future[ServiceResult[Option[Activity]]] =
              if (removedOwnersToNotify.nonEmpty)
                notificationService.ownerCancelledAppointment(removedOwnersToNotify, appointment).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            val notifyExistingClients: Future[ServiceResult[Option[Activity]]] =
              if (clientsResult.unchanged.nonEmpty)
                notificationService.clientChangedAppointment(clientsResult.unchanged.map(_.universityID).toSet).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            val existingOwnersToNotify = setOwnersResult.unchanged.map(_.userId).toSet - currentUser
            val notifyExistingOwners: Future[ServiceResult[Option[Activity]]] =
              if (existingOwnersToNotify.nonEmpty)
                notificationService.ownerChangedAppointment(existingOwnersToNotify, appointment).map(sr =>
                  ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
                )
              else Future.successful(Right(None))

            ServiceResults.zip(
              notifyAddedClients,
              notifyRemovedClients,
              notifyExistingClients,
              notifyAddedOwners,
              notifyRemovedOwners,
              notifyExistingOwners
            ).map(_ => Right(appointment))
          }
        }
      }
    }
  }

  override def reschedule(id: UUID, changes: AppointmentSave, currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentReschedule, id.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        existing <- dao.findByIDQuery(id).result.head
        clients <- getClientsQuery(id).result
        updated <- dao.update(
          // We re-construct the whole StoredAppointment here so that missing a value will throw a compile error
          StoredAppointment(
            existing.id,
            existing.key,
            changes.start,
            changes.duration,
            changes.roomID,
            existing.team,
            existing.appointmentType,
            existing.purpose,
            AppointmentState.Provisional, // Re-scheduling always sets back to provisional
            None,
            existing.outcome,
            existing.dsaSupportAccessed,
            existing.dsaActionPoints,
            existing.dsaActionPointOther,
            existing.created,
            JavaTime.offsetDateTime
          ),
          version
        )
        _ <- DBIO.seq(clients.map { client =>
          dao.updateClient(
            client.copy(
              state = AppointmentState.Provisional,
              cancellationReason = None,
              attendanceState = None,
            )
          )
        }: _*)
      } yield (updated, clients)).flatMap { case (a, clients) =>
        ownerService.getAppointmentOwners(Set(a.id)).successMapTo(_.getOrElse(id, Set())).successFlatMapTo { teamMembers =>
          office365CalendarService.updateAppointment(a.id, teamMembers)

          val appointment = a.asAppointment

          val notifyClients: Future[ServiceResult[Option[Activity]]] =
            notificationService.clientRescheduledAppointment(clients.map(_.universityID).toSet).map(sr =>
              ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
            )

          val ownersToNotify = teamMembers.map(_.member.usercode) - currentUser
          val notifyOwners: Future[ServiceResult[Option[Activity]]] =
            if (ownersToNotify.nonEmpty)
              notificationService.ownerRescheduledAppointment(ownersToNotify, appointment).map(sr =>
                ServiceResults.logErrors(sr.right.map(Some(_)), logger, None)
              )
            else Future.successful(Right(None))

          ServiceResults.zip(
            notifyClients,
            notifyOwners,
          ).map(_ => {
            scheduleClientReminder(appointment)
            Right(appointment)
          })
        }
      }
    }

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Appointment]] =
    daoRunner.run(dao.findByIDQuery(id).result.head).map { a => ServiceResults.success(a.asAppointment) }.recover {
      case _: NoSuchElementException => ServiceResults.error[Appointment](s"Could not find an Appointment with ID $id")
    }

  override def findFull(id: UUID)(implicit t: TimingContext): Future[ServiceResult[AppointmentRender]] = {
    val query = dao.findByIDQuery(id)
    listForRender(query).map(_.map(_.head)).recover {
      case _: NoSuchElementException => ServiceResults.error[AppointmentRender](s"Could not find an Appointment with ID ${id.toString}")
    }
  }

  override def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    daoRunner.run(dao.findByIDsQuery(ids.toSet).result).map { appointments =>
      val lookup = appointments.map(_.asAppointment).groupBy(_.id).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Appointment with ID $id") })
    }

  override def findFull(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findByIDsQuery(ids.toSet)).successFlatMapTo { appointments =>
      val lookup = appointments.groupBy(_.appointment.id).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Future.successful(Right(ids.map(lookup.apply)))
      else
        Future.successful(Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Appointment with ID $id") }))
    }

  override def find(appointmentKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Appointment]] =
    daoRunner.run(dao.findByKeyQuery(appointmentKey).result.head).map { a => ServiceResults.success(a.asAppointment) }.recover {
      case _: NoSuchElementException => ServiceResults.error[Appointment](s"Could not find an Appointment with key ${appointmentKey.string}")
    }

  override def findForRender(appointmentKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[AppointmentRender]] =
    auditService.audit('AppointmentView, (a: AppointmentRender) => a.appointment.id.toString, 'Appointment, Json.obj()) {
      val query = dao.findByKeyQuery(appointmentKey)
      listForRender(query).map(_.map(_.head)).recover {
        case _: NoSuchElementException => ServiceResults.error[AppointmentRender](s"Could not find an Appointment with key ${appointmentKey.string}")
      }
    }

  private def listForRender(query: Query[Appointments, StoredAppointment, Seq])(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    daoRunner.run(
      for {
        withClients <- query.withClients.result
        withRoom <- query.withRoom.result
        withCase <- query.withCases.result
      } yield (withClients, withRoom, withCase)
    ).flatMap { case (withClients, withRoom, withCase) =>
      ownerService.getAppointmentOwners(withClients.map { case (a, _, _) => a.id }.toSet).successMapTo { teamMembers =>
        groupTuples(withClients, withRoom, withCase, teamMembers)
      }
    }

  override def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findByClientQuery(universityID))

  override def findForCase(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findByCaseQuery(caseID))

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    auditService.findRecentTargetIDsByOperation('AppointmentView, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => find(ids.map(UUID.fromString))
    ))

  override def countForClientBadge(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.countForClientBadge(universityID)).map(Right.apply)

  override def search(query: AppointmentSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    daoRunner.run(dao.searchQuery(query).take(limit).result).map { a => Right(a.map(_.asAppointment)) }

  override def findForSearch(query: AppointmentSearchQuery)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.searchQuery(query))

  override def countForSearch(query: AppointmentSearchQuery)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.searchQuery(query).length.result).map(Right.apply)

  override def findDeclinedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findDeclinedQuery.filter(_.team === team))

  override def findDeclinedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findDeclinedQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def findProvisionalAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findProvisionalQuery.filter(_.team === team))

  override def findProvisionalAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findProvisionalQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def findAppointmentsNeedingOutcome(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findNeedingOutcomeQuery.filter(_.team === team))

  override def findAppointmentsNeedingOutcome(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findNeedingOutcomeQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def findAcceptedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findAcceptedQuery.filter(_.team === team))

  override def countAcceptedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findAcceptedQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findAcceptedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findAcceptedQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def countAcceptedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      dao.findAcceptedQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .length.result
    ).map(Right.apply)

  override def findAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findAttendedQuery.filter(_.team === team))

  override def countAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findAttendedQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findAttendedQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def countAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      dao.findAttendedQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .length.result
    ).map(Right.apply)

  override def findCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findCancelledQuery.filter(_.team === team))

  override def countCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findCancelledQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(
      dao.findCancelledQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .map { case (a, _, _) => a }
    )

  override def countCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      dao.findCancelledQuery.withTeamMembers
        .filter { case (_, o, _) => o.userId === teamMember }
        .length.result
    ).map(Right.apply)

  private def getClientsQuery(id: UUID) =
    dao.findClientsQuery(Set(id))
      .filter(_.appointmentID === id)

  override def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentClient]]] =
    daoRunner.run(
      getClientsQuery(id)
        .withClients
        .result
    ).map(_.map { case (ac, c) => ac.asAppointmentClient(c.asClient) }).map(r => Right(r.toSet))

  override def getTeamMembers(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentTeamMember]]] =
    ownerService.getAppointmentOwners(Set(id)).successMapTo(_.getOrElse(id, Set()))

  override def clientAccept(appointmentID: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentAccept, appointmentID.toString, 'Appointment, Json.obj("universityID" -> universityID.string)) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        client <-
          getClientsQuery(appointmentID)
            .filter { c => c.universityID === universityID && c.isProvisional }
            .result.head
        _ <- dao.updateClient(
          client.copy(
            state = AppointmentState.Accepted,
            cancellationReason = None
          )
        )
        // If any clients has accepted the appointment, transition state to accepted
        updatedAppointment <-
          if (appointment.state == AppointmentState.Provisional)
            dao.update(appointment.copy(state = AppointmentState.Accepted), appointment.version)
          else
            DBIO.successful(appointment)
      } yield updatedAppointment).flatMap { a =>
        getTeamMembers(a.id).successFlatMapTo { teamMembers =>
          val appointment = a.asAppointment
          notificationService.appointmentConfirmation(appointment, teamMembers.map(_.member.usercode), AppointmentState.Accepted).map(sr =>
            ServiceResults.logErrors(sr, logger, ())
          ).map(_ => Right(appointment))
        }
      }
    }

  override def clientDecline(appointmentID: UUID, universityID: UniversityID, reason: AppointmentCancellationReason)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentDecline, appointmentID.toString, 'Appointment, Json.obj("universityID" -> universityID.string, "reason" -> reason.entryName)) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        clients <- getClientsQuery(appointmentID).result
        _ <- dao.updateClient(
          clients.find(_.universityID == universityID).get.copy(
            state = AppointmentState.Cancelled,
            cancellationReason = Some(reason)
          )
        )
        // A client declension should transition the state to Provisional if no clients have accepted
        updatedAppointment <-
          if (appointment.state == AppointmentState.Accepted && clients.forall { c => c.universityID == universityID || c.state != AppointmentState.Accepted })
            dao.update(appointment.copy(state = AppointmentState.Provisional), appointment.version)
          else
            DBIO.successful(appointment)
      } yield updatedAppointment).flatMap { a =>
        getTeamMembers(a.id).successFlatMapTo { teamMembers =>
          val appointment = a.asAppointment
          notificationService.appointmentConfirmation(appointment, teamMembers.map(_.member.usercode), AppointmentState.Cancelled).map(sr =>
            ServiceResults.logErrors(sr, logger, ())
          ).map(_ => Right(appointment))
        }
      }
    }

  override def recordOutcomes(appointmentID: UUID, clientAttendance: Map[UniversityID, (AppointmentClientAttendanceState, Option[AppointmentCancellationReason])], outcomes: AppointmentOutcomesSave, note: Option[CaseNoteSave], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentOutcomes, appointmentID.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).filter(_.version === version).result.head
        cases <- dao.findByIDQuery(appointmentID).withCases.map { case (_, c) => c }.result
        clients <- getClientsQuery(appointmentID).result

        // Update individual clients
        _ <- DBIO.seq(clients.map { client =>
          clientAttendance.get(client.universityID) match {
            case Some((s, reason)) if client.state != s.appointmentState || !client.attendanceState.contains(s) =>
              dao.updateClient(
                client.copy(
                  state = s.appointmentState,
                  cancellationReason =
                    if (s.appointmentState == AppointmentState.Cancelled) reason
                    else None,
                  attendanceState = Some(s)
                )
              )

            case Some((s, _)) if s.appointmentState == AppointmentState.Attended && client.cancellationReason.nonEmpty =>
              // Remove reason
              dao.updateClient(client.copy(cancellationReason = None))

            case Some((s, reason)) if s.appointmentState == AppointmentState.Cancelled && client.cancellationReason != reason =>
              // Set reason
              dao.updateClient(client.copy(cancellationReason = reason))

            case _ => DBIO.successful(()) // no-op
          }
        }: _*)

        // Set the appointment to attended if at least one client has attended and it's not already attended
        // else set it as cancelled with the first client reason
        updatedAppointment <- {
          val updates = appointment.copy(
            outcome = outcomes.outcome.map(_.entryName).toList.sorted,
            dsaSupportAccessed = outcomes.dsaSupportAccessed,
            dsaActionPoints = outcomes.dsaActionPoints.map(_.entryName).toList.sorted,
            dsaActionPointOther = AppointmentDSAActionPoint.otherValue(outcomes.dsaActionPoints),
          )

          if (clientAttendance.values.exists { case (s, _) => s.appointmentState == AppointmentState.Attended } && (appointment.state != AppointmentState.Attended || appointment.cancellationReason.nonEmpty))
            dao.update(updates.copy(state = AppointmentState.Attended, cancellationReason = None), version)
          else if (clientAttendance.values.forall { case (s, _) => s.appointmentState == AppointmentState.Cancelled } && (appointment.state != AppointmentState.Cancelled || clientAttendance.values.forall { case (_, r) => r != appointment.cancellationReason }))
            dao.update(updates.copy(state = AppointmentState.Cancelled, cancellationReason = clientAttendance.values.headOption.flatMap { case (_, r) => r }), version)
          else
            dao.update(updates, version)
        }

        _ <- note.map { n => DBIO.seq(cases.map { c => caseService.addNoteDBIO(c.id, CaseNoteType.AppointmentNote, n) }: _*) }.getOrElse(DBIO.successful(()))
      } yield updatedAppointment).map { a => Right(a.asAppointment) }
    }

  override def cancel(appointmentID: UUID, reason: AppointmentCancellationReason, note: Option[CaseNoteSave], currentUser: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentCancel, appointmentID.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        cases <- dao.findByIDQuery(appointmentID).withCases.map { case (_, c) => c }.result
        clients <- getClientsQuery(appointmentID).result
        updatedAppointment <- dao.update(appointment.copy(state = AppointmentState.Cancelled, cancellationReason = Some(reason)), version)
        _ <- note.map { n => DBIO.seq(cases.map { c => caseService.addNoteDBIO(c.id, CaseNoteType.AppointmentNote, n) }: _*) }.getOrElse(DBIO.successful(()))
      } yield (updatedAppointment, clients)).flatMap { case (a, clients) =>
        getTeamMembers(a.id).successFlatMapTo(teamMembers => {
          office365CalendarService.updateAppointment(a.id, teamMembers)
          val appointment = a.asAppointment
          val ownersToNotify = teamMembers.map(_.member.usercode) - currentUser
          val notifications = ServiceResults.zip(
            notificationService.clientCancelledAppointment(clients.map(_.universityID).toSet).map(sr => ServiceResults.logErrors(sr, logger, ())),
            notificationService.ownerCancelledAppointment(ownersToNotify, appointment).map(sr => ServiceResults.logErrors(sr, logger, ()))
          )
          (for {
            _ <- note.map(n => memberService.getOrAddMember(n.teamMember)).getOrElse(Future.successful(()))
            activities <- notifications
          } yield activities).map(_ =>
            ServiceResults.success(appointment)
          )
        })
      }
    }

  private def scheduleClientReminder(appointment: Appointment) = {
    if (clientRemindersEnabled) {
      val jobKey = new JobKey(appointment.id.toString, "SendAppointmentClientReminder")
      val triggerKey = new TriggerKey(appointment.id.toString, "SendAppointmentClientReminder")

      val triggerTime = appointment.start.minusDays(1).toInstant

      if (scheduler.checkExists(triggerKey)) {
        scheduler.rescheduleJob(
          triggerKey,
          TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(triggerTime))
            .build()
        )
      } else {
        scheduler.scheduleJob(
          JobBuilder.newJob(classOf[SendAppointmentClientReminderJob])
            .withIdentity(jobKey)
            .usingJobData("id", appointment.id.toString)
            .build(),
          TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(triggerTime))
            .build()
        )
      }
    }
  }

  override def sendClientReminder(appointmentID: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit('AppointmentSendClientReminder, appointmentID.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        clients <- getClientsQuery(appointmentID).result
      } yield (appointment, clients)).flatMap { case (appointment, clients) =>
        notificationService.appointmentReminder(appointment.asAppointment, clients.filterNot(_.state == AppointmentState.Cancelled).map(_.universityID).toSet).map(sr =>
          ServiceResults.logErrors(sr, logger, ())
        ).map(_ => Right(Done))
      }
    }

}

object AppointmentService {
  def groupTuples(
    withClients: Seq[(StoredAppointment, StoredAppointmentClient, StoredClient)],
    withRoom: Seq[(StoredAppointment, Room)],
    withCase: Seq[(StoredAppointment, StoredCase)],
    teamMembers: Map[UUID, Set[AppointmentTeamMember]],
  ): Seq[AppointmentRender] = {
    val appointmentsAndClients = OneToMany.join(withClients.map {
      case (a, ac, c) => (a.asAppointment, ac.asAppointmentClient(c.asClient))
    })(Ordering.by[AppointmentClient, String](_.client.universityID.string))
    val roomByAppointment = withRoom.map { case (a, r) => a.id -> r }.toMap
    val appointmentAndCases = OneToMany.join(withCase.map { case (a, c) => (a.id, c.asCase) })(Case.dateOrdering).toMap

    appointmentsAndClients.map { case (appointment, clients) => AppointmentRender(
      appointment = appointment,
      clients = clients.toSet,
      teamMembers = teamMembers.getOrElse(appointment.id, Set()),
      room = roomByAppointment.get(appointment.id),
      appointmentAndCases.getOrElse(appointment.id, Seq()).toSet,
    ) }
      .sortBy(_.appointment.start)(JavaTime.dateTimeOrdering)
  }
}