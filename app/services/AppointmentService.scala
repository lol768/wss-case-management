package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.{AppointmentSearchQuery, Appointments, StoredAppointment, StoredAppointmentClient}
import domain.dao.CaseDao.Case
import domain.dao.{AppointmentDao, DaoRunner}
import helpers.{JavaTime, ServiceResults}
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.AppointmentService._
import system.Logging
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[AppointmentServiceImpl])
trait AppointmentService {
  def create(appointment: AppointmentSave, clients: Set[UniversityID], team: Team, caseID: Option[UUID])(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def update(id: UUID, appointment: AppointmentSave, clients: Set[UniversityID], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]

  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Appointment]]
  def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]
  def find(appointmentKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Appointment]]
  def findForRender(appointmentKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[AppointmentRender]]
  def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findForCase(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findForCaseDBIO(caseID: UUID)(implicit t: TimingContext): DBIO[Seq[Appointment]]
  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]

  def search(query: AppointmentSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]]

  def findRejectedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findRejectedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findProvisionalAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findProvisionalAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findAppointmentsNeedingOutcome(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findAppointmentsNeedingOutcome(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def findConfirmedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countConfirmedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findConfirmedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countConfirmedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]]
  def countCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]]

  def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentClient]]]

  def clientAccept(appointmentID: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
  def clientReject(appointmentID: UUID, universityID: UniversityID, reason: AppointmentCancellationReason)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]]
}

@Singleton
class AppointmentServiceImpl @Inject()(
  auditService: AuditService,
  notificationService: NotificationService,
  daoRunner: DaoRunner,
  dao: AppointmentDao
)(implicit ec: ExecutionContext) extends AppointmentService with Logging {

  private def createStoredAppointment(id: UUID, key: IssueKey, save: AppointmentSave, team: Team, caseID: Option[UUID]): StoredAppointment =
    StoredAppointment(
      id,
      key,
      caseID,
      save.subject,
      save.start,
      save.duration,
      save.location,
      team,
      save.teamMember,
      save.appointmentType,
      AppointmentState.Provisional,
      None,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime
    )

  override def create(appointment: AppointmentSave, clients: Set[UniversityID], team: Team, caseID: Option[UUID])(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] = {
    val id = UUID.randomUUID()
    auditService.audit('AppointmentSave, id.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        nextId <- sql"SELECT nextval('SEQ_APPOINTMENT_KEY')".as[Int].head
        inserted <- dao.insert(createStoredAppointment(id, IssueKey(IssueKeyType.Appointment, nextId), appointment, team, caseID))
        _ <- dao.insertClients(clients.map { universityID =>
          StoredAppointmentClient(
            universityID,
            inserted.id,
            AppointmentState.Provisional,
            None,
            JavaTime.offsetDateTime,
            JavaTime.offsetDateTime
          )
        })
      } yield inserted).map { a => Right(a.asAppointment) }
    }
  }

  override def update(id: UUID, changes: AppointmentSave, clients: Set[UniversityID], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] = {
    auditService.audit('AppointmentUpdate, id.toString, 'Appointment, Json.obj()) {
      daoRunner.run(for {
        existing <- dao.findByIDQuery(id).result.head
        updated <- dao.update(
          // We re-construct the whole StoredAppointment here so that missing a value will throw a compile error
          StoredAppointment(
            existing.id,
            existing.key,
            existing.caseID,
            changes.subject,
            changes.start,
            changes.duration,
            changes.location,
            existing.team,
            changes.teamMember,
            changes.appointmentType,
            AppointmentState.Provisional,
            None,
            JavaTime.offsetDateTime,
            JavaTime.offsetDateTime
          ),
          version
        )





      } yield existing).map { a => Right(a.asAppointment) }
    }
  }

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Appointment]] =
    daoRunner.run(dao.findByIDQuery(id).map(_.appointment).result.head)
      .map(Right.apply)

  override def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    daoRunner.run(dao.findByIDsQuery(ids.toSet).map(_.appointment).result).map { appointments =>
      val lookup = appointments.groupBy(_.id).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find an Appointment with ID $id") })
    }

  override def find(appointmentKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Appointment]] =
    daoRunner.run(dao.findByKeyQuery(appointmentKey).map(_.appointment).result.head)
      .map(Right.apply)

  override def findForRender(appointmentKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[AppointmentRender]] =
    auditService.audit('AppointmentView, (a: AppointmentRender) => a.appointment.id.toString, 'Appointment, Json.obj()) {
      val query = dao.findByKeyQuery(appointmentKey)
      daoRunner.run(for {
        withCase <- query.withCase.map { case (a, c) => (a.appointment, c) }.result.head
        clients <- query.withClients.map { case (_, c) => c.appointmentClient }.result
      } yield AppointmentRender(withCase._1, clients.toSet, withCase._2)).map(Right.apply)
    }

  private def listForRenderDBIO(query: Query[Appointments, StoredAppointment, Seq]): DBIO[(Seq[(Appointment, AppointmentClient)], Seq[(Appointment, Option[Case])])] =
    for {
      withClients <- query.withClients.map { case (a, c) => (a.appointment, c.appointmentClient) }.result
      withCase <- query.withCase.map { case (a, c) => (a.appointment, c) }.result
    } yield (withClients, withCase)

  private def listForRender(query: Query[Appointments, StoredAppointment, Seq])(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    daoRunner.run(listForRenderDBIO(query))
      .map { case (withClients, withCase) => Right(groupTuples(withClients, withCase)) }

  override def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findByClientQuery(universityID))

  override def findForCase(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findByCaseQuery(caseID))

  override def findForCaseDBIO(caseID: UUID)(implicit t: TimingContext): DBIO[Seq[Appointment]] =
    dao.findByCaseQuery(caseID).map(_.appointment).result

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    auditService.findRecentTargetIDsByOperation('AppointmentView, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => find(ids.map(UUID.fromString))
    ))

  override def search(query: AppointmentSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Appointment]]] =
    ???

  override def findRejectedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findRejectedQuery.filter(_.team === team))

  override def findRejectedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findRejectedQuery.filter(_.teamMember === teamMember))

  override def findProvisionalAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findProvisionalQuery.filter(_.team === team))

  override def findProvisionalAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findProvisionalQuery.filter(_.teamMember === teamMember))

  override def findAppointmentsNeedingOutcome(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findNeedingOutcomeQuery.filter(_.team === team))

  override def findAppointmentsNeedingOutcome(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findNeedingOutcomeQuery.filter(_.teamMember === teamMember))

  override def findConfirmedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findConfirmedQuery.filter(_.team === team))

  override def countConfirmedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findConfirmedQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findConfirmedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findConfirmedQuery.filter(_.teamMember === teamMember))

  override def countConfirmedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findConfirmedQuery.filter(_.teamMember === teamMember).length.result)
      .map(Right.apply)

  override def findAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findAttendedQuery.filter(_.team === team))

  override def countAttendedAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findAttendedQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findAttendedQuery.filter(_.teamMember === teamMember))

  override def countAttendedAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findAttendedQuery.filter(_.teamMember === teamMember).length.result)
      .map(Right.apply)

  override def findCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findCancelledQuery.filter(_.team === team))

  override def countCancelledAppointments(team: Team)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findCancelledQuery.filter(_.team === team).length.result)
      .map(Right.apply)

  override def findCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[AppointmentRender]]] =
    listForRender(dao.findCancelledQuery.filter(_.teamMember === teamMember))

  override def countCancelledAppointments(teamMember: Usercode)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.findCancelledQuery.filter(_.teamMember === teamMember).length.result)
      .map(Right.apply)

  private def getClientsDBIO(id: UUID): DBIO[Seq[StoredAppointmentClient]] =
    dao.findClientsQuery(Set(id))
      .filter(_.appointmentID === id)
      .result

  override def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[AppointmentClient]]] =
    daoRunner.run(
      dao.findClientsQuery(Set(id))
        .filter(_.appointmentID === id)
        .map(_.appointmentClient)
        .result
    ).map { c => Right(c.toSet) }

  override def clientAccept(appointmentID: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentAccept, appointmentID.toString, 'Appointment, Json.obj("universityID" -> universityID.string)) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        clients <- getClientsDBIO(appointmentID)
        _ <- dao.updateClient(
          clients.find(_.universityID == universityID).get.copy(
            state = AppointmentState.Confirmed,
            cancellationReason = None
          )
        )
        // If any clients has accepted the appointment, transition state to confirmed
        updatedAppointment <-
          if (appointment.state == AppointmentState.Provisional)
            dao.update(appointment.copy(state = AppointmentState.Confirmed), appointment.version)
          else
            DBIO.successful(appointment)
      } yield updatedAppointment).flatMap { a =>
        notificationService.appointmentConfirmation(a.asAppointment, AppointmentState.Confirmed).map(sr =>
          ServiceResults.logErrors(sr, logger, ())
        ).map(_ => Right(a.asAppointment))
      }
    }

  override def clientReject(appointmentID: UUID, universityID: UniversityID, reason: AppointmentCancellationReason)(implicit ac: AuditLogContext): Future[ServiceResult[Appointment]] =
    auditService.audit('AppointmentReject, appointmentID.toString, 'Appointment, Json.obj("universityID" -> universityID.string, "reason" -> reason.entryName)) {
      daoRunner.run(for {
        appointment <- dao.findByIDQuery(appointmentID).result.head
        clients <- getClientsDBIO(appointmentID)
        _ <- dao.updateClient(
          clients.find(_.universityID == universityID).get.copy(
            state = AppointmentState.Cancelled,
            cancellationReason = Some(reason)
          )
        )
        // A client rejection should transition the state to Provisional if no clients have confirmed
        updatedAppointment <-
          if (appointment.state == AppointmentState.Confirmed && clients.forall { c => c.universityID == universityID || c.state != AppointmentState.Confirmed })
            dao.update(appointment.copy(state = AppointmentState.Provisional), appointment.version)
          else
            DBIO.successful(appointment)
      } yield updatedAppointment).flatMap { a =>
        notificationService.appointmentConfirmation(a.asAppointment, AppointmentState.Cancelled).map(sr =>
          ServiceResults.logErrors(sr, logger, ())
        ).map(_ => Right(a.asAppointment))
      }
    }

}

object AppointmentService {
  def groupTuples(withClients: Seq[(Appointment, AppointmentClient)], withCase: Seq[(Appointment, Option[Case])]): Seq[AppointmentRender] = {
    val appointmentsAndCase = withCase.toMap

    OneToMany.join(withClients)(Ordering.by[AppointmentClient, String](_.universityID.string))
      .map { case (app, clients) =>
        AppointmentRender(
          app,
          clients.toSet,
          appointmentsAndCase.getOrElse(app, None)
        )
      }
      .sortBy(_.appointment.start)(JavaTime.dateTimeOrdering)
  }
}