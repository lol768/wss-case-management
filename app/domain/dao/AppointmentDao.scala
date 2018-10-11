package domain.dao
import java.time.{Duration, LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao._
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[AppointmentDaoImpl])
trait AppointmentDao {
  def insert(appointment: StoredAppointment)(implicit ac: AuditLogContext): DBIO[StoredAppointment]
  def update(appointment: StoredAppointment, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointment]

  def findByIDQuery(id: UUID): Query[Appointments, StoredAppointment, Seq]
  def findByIDsQuery(ids: Set[UUID]): Query[Appointments, StoredAppointment, Seq]
  def findByKeyQuery(key: IssueKey): Query[Appointments, StoredAppointment, Seq]
  def findByClientQuery(universityID: UniversityID): Query[Appointments, StoredAppointment, Seq]
  def findByCaseQuery(caseID: UUID): Query[Appointments, StoredAppointment, Seq]
  def findDeclinedQuery: Query[Appointments, StoredAppointment, Seq]
  def findProvisionalQuery: Query[Appointments, StoredAppointment, Seq]
  def findNeedingOutcomeQuery: Query[Appointments, StoredAppointment, Seq]
  def findAcceptedQuery: Query[Appointments, StoredAppointment, Seq]
  def findAttendedQuery: Query[Appointments, StoredAppointment, Seq]
  def findCancelledQuery: Query[Appointments, StoredAppointment, Seq]
  def searchQuery(query: AppointmentSearchQuery): Query[Appointments, StoredAppointment, Seq]


  def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]]
  def updateClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient]
  def deleteClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Done]
  def findClientByIDQuery(appointmentID: UUID, universityID: UniversityID): Query[AppointmentClients, StoredAppointmentClient, Seq]
  def findClientsQuery(appointmentIDs: Set[UUID]): Query[AppointmentClients, StoredAppointmentClient, Seq]

  def insertNote(note: StoredAppointmentNote)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote]
  def updateNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote]
  def deleteNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findNotesQuery(appointmentID: UUID): Query[AppointmentNotes, StoredAppointmentNote, Seq]
}

@Singleton
class AppointmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends AppointmentDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(appointment: StoredAppointment)(implicit ac: AuditLogContext): DBIO[StoredAppointment] =
    appointments.insert(appointment)

  override def update(appointment: StoredAppointment, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointment] =
    appointments.update(appointment.copy(version = version))

  override def findByIDQuery(id: UUID): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.filter(_.id === id)

  override def findByIDsQuery(ids: Set[UUID]): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.filter(_.id.inSet(ids))

  override def findByKeyQuery(key: IssueKey): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.filter(_.key === key)

  override def findByClientQuery(universityID: UniversityID): Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .withClients
      .filter { case (_, client) => client.universityID === universityID }
      .map { case (a, _) => a }

  override def findByCaseQuery(caseID: UUID): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.filter(_.caseID === caseID)

  override def findDeclinedQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .withClients
      .filter { case (a, client) =>
        a.isProvisional && client.isDeclined
      }
      .map { case (a, _) => a }
      .distinct

  override def findProvisionalQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .filter { a =>
        a.isProvisional &&
        a.isInFuture &&
        !a.id.in(
          appointmentClients.table
            .filter { client =>
              client.appointmentID === a.id && client.isResponded
            }
            .map(_.appointmentID)
        )
      }

  override def findNeedingOutcomeQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .filter { a =>
        a.isAccepted && a.isInPast
      }

  override def findAcceptedQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .filter { a =>
        a.isAccepted && a.isInFuture
      }

  override def findAttendedQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .filter(_.isAttended)

  override def findCancelledQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .filter(_.isCancelled)

  override def searchQuery(q: AppointmentSearchQuery): Query[Appointments, StoredAppointment, Seq] = {
    def queries(a: Appointments, n: Rep[Option[AppointmentNotes]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          n.map(_.searchableText) @@ plainToTsQuery(queryStr.bind, Some("english"))
        },
        q.createdAfter.map { d => a.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => a.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.startAfter.map { d => a.start.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.startBefore.map { d => a.start.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.location.map { location => a.searchableLocation.? @@ plainToTsQuery(location.name, Some("english")) },
        q.teamMember.map { member => a.teamMember.? === member },
        q.team.map { team => a.team.? === team },
        q.teamMember.map { member => a.teamMember.? === member },
        q.appointmentType.map { appointmentType => a.appointmentType.? === appointmentType },
        q.states.headOption.map { _ => a.state.inSet(q.states).? }
      ).flatten

    appointments.table
      .withNotes
      .filter { case (a, n) => queries(a, n).reduce(_ && _) }
      .distinct
      .map { case (a, _) => a }
  }

  override def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]] =
    appointmentClients.insertAll(clients.toSeq)

  override def updateClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient] =
    appointmentClients.update(client)

  override def deleteClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Done] =
    appointmentClients.deleteAll(clients.toSeq)

  override def findClientByIDQuery(appointmentID: UUID, universityID: UniversityID): Query[AppointmentClients, StoredAppointmentClient, Seq] =
    appointmentClients.table.filter { c => c.appointmentID === appointmentID && c.universityID === universityID }

  override def findClientsQuery(appointmentIDs: Set[UUID]): Query[AppointmentClients, StoredAppointmentClient, Seq] =
    appointmentClients.table
      .filter(_.appointmentID.inSet(appointmentIDs))

  override def insertNote(note: StoredAppointmentNote)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote] =
    appointmentNotes.insert(note)

  override def updateNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote] =
    appointmentNotes.update(note.copy(version = version))

  override def deleteNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    appointmentNotes.delete(note.copy(version = version))

  override def findNotesQuery(appointmentID: UUID): Query[AppointmentNotes, StoredAppointmentNote, Seq] =
    appointmentNotes.table.filter(_.appointmentId === appointmentID)

}

object AppointmentDao {
  val appointments: VersionedTableQuery[StoredAppointment, StoredAppointmentVersion, Appointments, AppointmentVersions] =
    VersionedTableQuery(TableQuery[Appointments], TableQuery[AppointmentVersions])

  val appointmentClients: VersionedTableQuery[StoredAppointmentClient, StoredAppointmentClientVersion, AppointmentClients, AppointmentClientVersions] =
    VersionedTableQuery(TableQuery[AppointmentClients], TableQuery[AppointmentClientVersions])

  val appointmentNotes: VersionedTableQuery[StoredAppointmentNote, StoredAppointmentNoteVersion, AppointmentNotes, AppointmentNoteVersions] =
    VersionedTableQuery(TableQuery[AppointmentNotes], TableQuery[AppointmentNoteVersions])

  case class StoredAppointment(
    id: UUID,
    key: IssueKey,
    caseID: Option[UUID],
    start: OffsetDateTime,
    duration: Duration,
    location: Option[Location],
    team: Team,
    teamMember: Usercode,
    appointmentType: AppointmentType,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointment] {
    def asAppointment = Appointment(
      id,
      key,
      start,
      duration,
      location,
      team,
      teamMember,
      appointmentType,
      state,
      cancellationReason,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredAppointment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentVersion(
        id,
        key,
        caseID,
        start,
        duration,
        location,
        team,
        teamMember,
        appointmentType,
        state,
        cancellationReason,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAppointmentVersion(
    id: UUID,
    key: IssueKey,
    caseID: Option[UUID],
    start: OffsetDateTime,
    duration: Duration,
    location: Option[Location],
    team: Team,
    teamMember: Usercode,
    appointmentType: AppointmentType,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAppointment]

  trait CommonAppointmentProperties { self: Table[_] =>
    def key = column[IssueKey]("appointment_key")
    def searchableKey = toTsVector(key.asColumnOf[String], Some("english"))
    def caseID = column[Option[UUID]]("case_id")
    def start = column[OffsetDateTime]("start_utc")
    def duration = column[Duration]("duration_secs")
    def location = column[Option[Location]]("location")
    def searchableLocation = toTsVector(location.asColumnOf[String], Some("english"))
    def team = column[Team]("team_id")
    def teamMember = column[Usercode]("team_member")
    def appointmentType = column[AppointmentType]("appointment_type")
    def state = column[AppointmentState]("state")
    def cancellationReason = column[Option[AppointmentCancellationReason]]("cancellation_reason")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Appointments(tag: Tag) extends Table[StoredAppointment](tag, "appointment")
    with VersionedTable[StoredAppointment]
    with CommonAppointmentProperties {
    override def matchesPrimaryKey(other: StoredAppointment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAppointment] =
      (id, key, caseID, start, duration, location, team, teamMember, appointmentType, state, cancellationReason, created, version).mapTo[StoredAppointment]
    def appointment =
      (id, key, start, duration, location, team, teamMember, appointmentType, state, cancellationReason, created, version).mapTo[Appointment]

    def isProvisional: Rep[Boolean] = state === (AppointmentState.Provisional: AppointmentState)
    def isAccepted: Rep[Boolean] = state === (AppointmentState.Accepted: AppointmentState)
    def isAttended: Rep[Boolean] = state === (AppointmentState.Attended: AppointmentState)
    def isCancelled: Rep[Boolean] = state === (AppointmentState.Cancelled: AppointmentState)
    def isInFuture: Rep[Boolean] = start >= JavaTime.offsetDateTime
    def isInPast: Rep[Boolean] = !isInFuture

    def keyIndex = index("idx_appointment_key", key, unique = true)
    def teamIndex = index("idx_appointment_team", (start, team))
    def teamMemberIndex = index("idx_appointment_team_member", (start, teamMember))
    def caseIndex = index("idx_appointment_case", caseID)
    def caseFK = foreignKey("fk_appointment_case", caseID, CaseDao.cases.table)(_.id.?)
    def stateIndex = index("idx_appointment_state", state)
  }

  class AppointmentVersions(tag: Tag) extends Table[StoredAppointmentVersion](tag, "appointment_version")
    with StoredVersionTable[StoredAppointment]
    with CommonAppointmentProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentVersion] =
      (id, key, caseID, start, duration, location, team, teamMember, appointmentType, state, cancellationReason, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentVersion]
    def pk = primaryKey("pk_appointment_version", (id, timestamp))
    def idx = index("idx_appointment_version", (id, version))
  }

  implicit class AppointmentExtensions[C[_]](q: Query[Appointments, StoredAppointment, C]) {
    def withClients = q
      .join(appointmentClients.table)
      .on(_.id === _.appointmentID)
    def withCase = q
      .joinLeft(CaseDao.cases.table)
      .on(_.caseID === _.id)
    def withNotes = q
      .joinLeft(appointmentNotes.table)
      .on(_.id === _.appointmentId)
  }

  case class StoredAppointmentClient(
    universityID: UniversityID,
    appointmentID: UUID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointmentClient] {
    def asAppointmentClient = AppointmentClient(
      universityID,
      state,
      cancellationReason
    )

    override def atVersion(at: OffsetDateTime): StoredAppointmentClient = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointmentClient]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentClientVersion(
        universityID,
        appointmentID,
        state,
        cancellationReason,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAppointmentClientVersion(
    universityID: UniversityID,
    appointmentID: UUID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAppointmentClient]

  trait CommonAppointmentClientProperties { self: Table[_] =>
    def universityID = column[UniversityID]("university_id")
    def appointmentID = column[UUID]("appointment_id")
    def state = column[AppointmentState]("state")
    def cancellationReason = column[Option[AppointmentCancellationReason]]("cancellation_reason")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class AppointmentClients(tag: Tag) extends Table[StoredAppointmentClient](tag, "appointment_client")
    with VersionedTable[StoredAppointmentClient]
    with CommonAppointmentClientProperties {
    override def matchesPrimaryKey(other: StoredAppointmentClient): Rep[Boolean] =
      universityID === other.universityID && appointmentID === other.appointmentID

    override def * : ProvenShape[StoredAppointmentClient] =
      (universityID, appointmentID, state, cancellationReason, created, version).mapTo[StoredAppointmentClient]
    def appointmentClient =
      (universityID, state, cancellationReason).mapTo[AppointmentClient]

    def isProvisional: Rep[Boolean] = state === (AppointmentState.Provisional: AppointmentState)
    def isAccepted: Rep[Boolean] = state === (AppointmentState.Accepted: AppointmentState)
    def isAttended: Rep[Boolean] = state === (AppointmentState.Attended: AppointmentState)
    def isDeclined: Rep[Boolean] = state === (AppointmentState.Cancelled: AppointmentState)
    def isResponded: Rep[Boolean] = isAccepted || isDeclined

    def pk = primaryKey("pk_appointment_client", (universityID, appointmentID))
    def fk = foreignKey("fk_appointment_client", appointmentID, appointments.table)(_.id)
    def idx = index("idx_appointment_client", appointmentID)
  }

  class AppointmentClientVersions(tag: Tag) extends Table[StoredAppointmentClientVersion](tag, "appointment_client_version")
    with StoredVersionTable[StoredAppointmentClient]
    with CommonAppointmentClientProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentClientVersion] =
      (universityID, appointmentID, state, cancellationReason, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentClientVersion]
    def pk = primaryKey("pk_appointment_client_version", (universityID, appointmentID, timestamp))
    def idx = index("idx_appointment_client_version", (universityID, appointmentID, version))
  }

  case class StoredAppointmentNote(
    id: UUID,
    appointmentId: UUID,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAppointmentNote] {
    def asAppointmentNote = AppointmentNote(
      id,
      text,
      teamMember,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredAppointmentNote = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointmentNote]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentNoteVersion(
        id,
        appointmentId,
        text,
        teamMember,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAppointmentNoteVersion(
    id: UUID,
    appointmentId: UUID,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAppointmentNote]

  trait CommonNoteProperties { self: Table[_] =>
    def appointmentId = column[UUID]("appointment_id")
    def text = column[String]("text")
    def searchableText = toTsVector(text, Some("english"))
    def teamMember = column[Usercode]("team_member")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class AppointmentNotes(tag: Tag) extends Table[StoredAppointmentNote](tag, "appointment_note")
    with VersionedTable[StoredAppointmentNote]
    with CommonNoteProperties {
    override def matchesPrimaryKey(other: StoredAppointmentNote): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAppointmentNote] =
      (id, appointmentId, text, teamMember, created, version).mapTo[StoredAppointmentNote]
    def fk = foreignKey("fk_appointment_note", appointmentId, appointments.table)(_.id)
    def idx = index("idx_appointment_note", appointmentId)
  }

  class AppointmentNoteVersions(tag: Tag) extends Table[StoredAppointmentNoteVersion](tag, "appointment_note_version")
    with StoredVersionTable[StoredAppointmentNote]
    with CommonNoteProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentNoteVersion] =
      (id, appointmentId, text, teamMember, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentNoteVersion]
    def pk = primaryKey("pk_appointment_note_version", (id, timestamp))
    def idx = index("idx_appointment_note_version", (id, version))
  }

  case class AppointmentSearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    startAfter: Option[LocalDate] = None,
    startBefore: Option[LocalDate] = None,
    location: Option[Location] = None,
    team: Option[Team] = None,
    teamMember: Option[Usercode] = None,
    appointmentType: Option[AppointmentType] = None,
    states: Set[AppointmentState] = Set()
  ) {
    def isEmpty: Boolean = !nonEmpty
    def nonEmpty: Boolean =
      query.exists(_.hasText) ||
      createdAfter.nonEmpty ||
      createdBefore.nonEmpty ||
      startAfter.nonEmpty ||
      startBefore.nonEmpty ||
      team.nonEmpty ||
      teamMember.nonEmpty ||
      location.nonEmpty ||
      appointmentType.nonEmpty ||
      states.nonEmpty
  }
}