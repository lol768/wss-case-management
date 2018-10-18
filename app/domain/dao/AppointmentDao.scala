package domain.dao

import java.time.{Duration, LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.AppointmentCase.AppointmentCases
import domain.dao.AppointmentDao._
import domain.dao.CaseDao.cases
import helpers.JavaTime
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}
import QueryHelpers._
import domain.dao.ClientDao.StoredClient.Clients

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

  def casesForAppointmentQuery(appointment: UUID): Query[AppointmentCases, AppointmentCase, Seq]
  def insertCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Seq[AppointmentCase]]
  def deleteCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Done]
  def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]]
  def updateClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient]
  def deleteClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Done]
  def findClientByIDQuery(appointmentID: UUID, universityID: UniversityID): Query[AppointmentClients, StoredAppointmentClient, Seq]
  def findClientsQuery(appointmentIDs: Set[UUID]): Query[AppointmentClients, StoredAppointmentClient, Seq]
  def insertTeamMembers(teamMembers: Set[StoredAppointmentTeamMember])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentTeamMember]]
  def updateTeamMember(teamMember: StoredAppointmentTeamMember)(implicit ac: AuditLogContext): DBIO[StoredAppointmentTeamMember]
  def deleteTeamMembers(teamMembers: Set[StoredAppointmentTeamMember])(implicit ac: AuditLogContext): DBIO[Done]
  def findTeamMemberByIDQuery(appointmentID: UUID, usercode: Usercode): Query[AppointmentTeamMembers, StoredAppointmentTeamMember, Seq]
  def findTeamMembersQuery(appointmentIDs: Set[UUID]): Query[AppointmentTeamMembers, StoredAppointmentTeamMember, Seq]

  def insertNote(note: StoredAppointmentNote)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote]
  def updateNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote]
  def deleteNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done]
  def findNotesQuery(appointmentID: UUID): Query[AppointmentNotes, StoredAppointmentNote, Seq]
  def findNotesQuery(appointmentIDs: Set[UUID]): Query[AppointmentNotes, StoredAppointmentNote, Seq]
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
      .filter { case (_, _, client) => client.universityID === universityID }
      .map { case (a, _, _) => a }

  override def findByCaseQuery(caseID: UUID): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.withCases
      .filter { case (_, clientCase) => clientCase.id === caseID }
      .map { case (a, _) => a }

  override def findDeclinedQuery: Query[Appointments, StoredAppointment, Seq] =
    appointments.table
      .withClients
      .filter { case (a, client, _) =>
        a.isProvisional && client.isDeclined
      }
      .map { case (a, _, _) => a }
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
    def queries(a: Appointments, c: Clients, tm: AppointmentTeamMembers, n: Rep[Option[AppointmentNotes]]): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          n.map(_.searchableText) @@ plainToTsQuery(queryStr.bind, Some("english"))
        },
        q.createdAfter.map { d => a.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => a.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.client.map { client => c.universityID.? === client },
        q.startAfter.map { d => a.start.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.startBefore.map { d => a.start.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.team.map { team => a.team.? === team },
        q.teamMember.map { member => tm.usercode.? === member },
        q.roomID.map { roomID => a.roomID === roomID },
        q.appointmentType.map { appointmentType => a.appointmentType.? === appointmentType },
        q.states.headOption.map { _ => a.state.inSet(q.states).? }
      ).flatten

    appointments.table
      .withClientsAndTeamMembersAndNotes
      .filter { case (a, _, c, tm, _, n) => queries(a, c, tm, n).reduce(_ && _) }
      .distinct
      .map { case (a, _, _, _, _, _) => a }
  }

  override def casesForAppointmentQuery(appointmentId: UUID): Query[AppointmentCases, AppointmentCase, Seq] = {
    AppointmentCase.appointmentCases.table.filter(ac => ac.appointmentID === appointmentId)
  }

  override def insertCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Seq[AppointmentCase]] =
    AppointmentCase.appointmentCases.insertAll(joins.toSeq)

  override def deleteCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Done] =
    AppointmentCase.appointmentCases.deleteAll(joins.toSeq)

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

  override def insertTeamMembers(teamMembers: Set[StoredAppointmentTeamMember])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentTeamMember]] =
    appointmentTeamMembers.insertAll(teamMembers.toSeq)

  override def updateTeamMember(teamMember: StoredAppointmentTeamMember)(implicit ac: AuditLogContext): DBIO[StoredAppointmentTeamMember] =
    appointmentTeamMembers.update(teamMember)

  override def deleteTeamMembers(teamMembers: Set[StoredAppointmentTeamMember])(implicit ac: AuditLogContext): DBIO[Done] =
    appointmentTeamMembers.deleteAll(teamMembers.toSeq)

  override def findTeamMemberByIDQuery(appointmentID: UUID, usercode: Usercode): Query[AppointmentTeamMembers, StoredAppointmentTeamMember, Seq] =
    appointmentTeamMembers.table.filter { m => m.appointmentID === appointmentID && m.usercode === usercode }

  override def findTeamMembersQuery(appointmentIDs: Set[UUID]): Query[AppointmentTeamMembers, StoredAppointmentTeamMember, Seq] =
    appointmentTeamMembers.table
      .filter(_.appointmentID.inSet(appointmentIDs))

  override def insertNote(note: StoredAppointmentNote)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote] =
    appointmentNotes.insert(note)

  override def updateNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredAppointmentNote] =
    appointmentNotes.update(note.copy(version = version))

  override def deleteNote(note: StoredAppointmentNote, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Done] =
    appointmentNotes.delete(note.copy(version = version))

  override def findNotesQuery(appointmentID: UUID): Query[AppointmentNotes, StoredAppointmentNote, Seq] =
    appointmentNotes.table.filter(_.appointmentId === appointmentID)

  override def findNotesQuery(appointmentIDs: Set[UUID]): Query[AppointmentNotes, StoredAppointmentNote, Seq] =
    appointmentNotes.table.filter(_.appointmentId.inSet(appointmentIDs))

}

object AppointmentDao {
  val appointments: VersionedTableQuery[StoredAppointment, StoredAppointmentVersion, Appointments, AppointmentVersions] =
    VersionedTableQuery(TableQuery[Appointments], TableQuery[AppointmentVersions])

  val appointmentClients: VersionedTableQuery[StoredAppointmentClient, StoredAppointmentClientVersion, AppointmentClients, AppointmentClientVersions] =
    VersionedTableQuery(TableQuery[AppointmentClients], TableQuery[AppointmentClientVersions])

  val appointmentTeamMembers: VersionedTableQuery[StoredAppointmentTeamMember, StoredAppointmentTeamMemberVersion, AppointmentTeamMembers, AppointmentTeamMemberVersions] =
    VersionedTableQuery(TableQuery[AppointmentTeamMembers], TableQuery[AppointmentTeamMemberVersions])

  val appointmentNotes: VersionedTableQuery[StoredAppointmentNote, StoredAppointmentNoteVersion, AppointmentNotes, AppointmentNoteVersions] =
    VersionedTableQuery(TableQuery[AppointmentNotes], TableQuery[AppointmentNoteVersions])

  case class StoredAppointment(
    id: UUID,
    key: IssueKey,
    start: OffsetDateTime,
    duration: Duration,
    roomID: Option[UUID],
    team: Team,
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
      team,
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
        start,
        duration,
        roomID,
        team,
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
    start: OffsetDateTime,
    duration: Duration,
    roomID: Option[UUID],
    team: Team,
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
    def start = column[OffsetDateTime]("start_utc")
    def duration = column[Duration]("duration_secs")
    def roomID = column[Option[UUID]]("room_id")
    def team = column[Team]("team_id")
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
      (id, key, start, duration, roomID, team, appointmentType, state, cancellationReason, created, version).mapTo[StoredAppointment]
    def appointment =
      (id, key, start, duration, team, appointmentType, state, cancellationReason, created, version).mapTo[Appointment]

    def isProvisional: Rep[Boolean] = state === (AppointmentState.Provisional: AppointmentState)
    def isAccepted: Rep[Boolean] = state === (AppointmentState.Accepted: AppointmentState)
    def isAttended: Rep[Boolean] = state === (AppointmentState.Attended: AppointmentState)
    def isCancelled: Rep[Boolean] = state === (AppointmentState.Cancelled: AppointmentState)
    def isInFuture: Rep[Boolean] = start >= JavaTime.offsetDateTime
    def isInPast: Rep[Boolean] = !isInFuture

    def keyIndex = index("idx_appointment_key", key, unique = true)
    def teamIndex = index("idx_appointment_team", (start, team))
    def stateIndex = index("idx_appointment_state", state)

    def roomFK = foreignKey("fk_appointment_room", roomID, LocationDao.rooms.table)(_.id.?)
    def roomIndex = index("idx_appointment_room", roomID)
  }

  class AppointmentVersions(tag: Tag) extends Table[StoredAppointmentVersion](tag, "appointment_version")
    with StoredVersionTable[StoredAppointment]
    with CommonAppointmentProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentVersion] =
      (id, key, start, duration, roomID, team, appointmentType, state, cancellationReason, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentVersion]
    def pk = primaryKey("pk_appointment_version", (id, timestamp))
    def idx = index("idx_appointment_version", (id, version))
  }

  implicit class AppointmentExtensions[C[_]](q: Query[Appointments, StoredAppointment, C]) {
    def withClients = q
      .join(appointmentClients.table)
      .on(_.id === _.appointmentID)
      .join(ClientDao.clients.table)
      .on { case ((_, ac), c) => ac.universityID === c.universityID }
      .flattenJoin
    def withCases = q
      .join(AppointmentCase.appointmentCases.table)
      .on(_.id === _.appointmentID)
      .join(CaseDao.cases.table)
      .on(_._2.caseID === _.id)
      .map { case ((a, _), c) => (a, c) }
    def withNotes = q
      .joinLeft(appointmentNotes.table)
      .on(_.id === _.appointmentId)
    def withRoom = q
      .join(LocationDao.rooms.table)
      .on(_.roomID === _.id)
      .join(LocationDao.buildings.table)
      .on(_._2.buildingID === _.id)
      .map { case ((a, r), b) => (a, (r.id, b.building, r.name, r.wai2GoID.getOrElse(b.wai2GoID), r.available, r.created, r.version).mapTo[Room]) }
    def withTeamMembers = q
      .join(appointmentTeamMembers.table)
      .on(_.id === _.appointmentID)
      .join(MemberDao.members.table)
      .on { case ((_, atm), m) => atm.usercode === m.usercode }
      .flattenJoin
    def withClientsAndTeamMembers = q
      .withClients
      .join(appointmentTeamMembers.table)
      .on(_._1.id === _.appointmentID)
      .flattenJoin
      .join(MemberDao.members.table)
      .on { case ((_, _, _, atm), m) => atm.usercode === m.usercode }
      .flattenJoin
    def withClientsAndTeamMembersAndNotes = q
      .withClientsAndTeamMembers
      .joinLeft(appointmentNotes.table)
      .on(_._1.id === _.appointmentId)
      .flattenJoin
  }

  case class StoredAppointmentClient(
    universityID: UniversityID,
    appointmentID: UUID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointmentClient] {
    def asAppointmentClient(client: Client) = AppointmentClient(
      client,
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

  implicit class AppointmentClientExtensions[C[_]](q: Query[AppointmentClients, StoredAppointmentClient, C]) {
    def withClients = q
      .join(ClientDao.clients.table)
      .on(_.universityID === _.universityID)
  }

  case class StoredAppointmentTeamMember(
    usercode: Usercode,
    appointmentID: UUID,
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointmentTeamMember] {
    def asAppointmentTeamMember(member: Member) = AppointmentTeamMember(member)

    override def atVersion(at: OffsetDateTime): StoredAppointmentTeamMember = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointmentTeamMember]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentTeamMemberVersion(
        usercode,
        appointmentID,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAppointmentTeamMemberVersion(
    usercode: Usercode,
    appointmentID: UUID,
    created: OffsetDateTime,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAppointmentTeamMember]

  trait CommonAppointmentTeamMemberProperties { self: Table[_] =>
    def usercode = column[Usercode]("usercode")
    def appointmentID = column[UUID]("appointment_id")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class AppointmentTeamMembers(tag: Tag) extends Table[StoredAppointmentTeamMember](tag, "appointment_team_member")
    with VersionedTable[StoredAppointmentTeamMember]
    with CommonAppointmentTeamMemberProperties {
    override def matchesPrimaryKey(other: StoredAppointmentTeamMember): Rep[Boolean] =
      usercode === other.usercode && appointmentID === other.appointmentID

    override def * : ProvenShape[StoredAppointmentTeamMember] =
      (usercode, appointmentID, created, version).mapTo[StoredAppointmentTeamMember]

    def pk = primaryKey("pk_appointment_team_member", (usercode, appointmentID))
    def appointmentFK = foreignKey("fk_appointment_team_member_appointment", appointmentID, appointments.table)(_.id)
    def appointmentIndex = index("idx_appointment_team_member_appointment", appointmentID)
    def teamMemberFK = foreignKey("fk_appointment_team_member_usercode", usercode, MemberDao.members.table)(_.usercode)
    def teamMemberIndex = index("idx_appointment_team_member_usercode", usercode)
  }

  class AppointmentTeamMemberVersions(tag: Tag) extends Table[StoredAppointmentTeamMemberVersion](tag, "appointment_team_member_version")
    with StoredVersionTable[StoredAppointmentTeamMember]
    with CommonAppointmentTeamMemberProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentTeamMemberVersion] =
      (usercode, appointmentID, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentTeamMemberVersion]
    def pk = primaryKey("pk_appointment_team_member_version", (usercode, appointmentID, timestamp))
    def idx = index("idx_appointment_team_member_version", (usercode, appointmentID, version))
  }

  implicit class AppointmentTeamMemberExtensions[C[_]](q: Query[AppointmentTeamMembers, StoredAppointmentTeamMember, C]) {
    def withMembers = q
      .join(MemberDao.members.table)
      .on(_.usercode === _.usercode)
  }

  case class StoredAppointmentNote(
    id: UUID,
    appointmentId: UUID,
    text: String,
    teamMember: Usercode,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAppointmentNote] {
    def asAppointmentNote(member: Member) = AppointmentNote(
      id,
      text,
      member,
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

  implicit class AppointmentNoteExtensions[C[_]](q: Query[AppointmentNotes, StoredAppointmentNote, C]) {
    def withMember = q
      .join(MemberDao.members.table)
      .on(_.teamMember === _.usercode)
  }

  case class AppointmentSearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    client: Option[UniversityID] = None,
    startAfter: Option[LocalDate] = None,
    startBefore: Option[LocalDate] = None,
    roomID: Option[UUID] = None,
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
      client.nonEmpty ||
      startAfter.nonEmpty ||
      startBefore.nonEmpty ||
      team.nonEmpty ||
      teamMember.nonEmpty ||
      roomID.nonEmpty ||
      appointmentType.nonEmpty ||
      states.nonEmpty
  }

  case class AppointmentCase(
    id: UUID,
    appointmentID: UUID,
    caseID: UUID,
    teamMember: Usercode,
    version: OffsetDateTime
  ) extends Versioned[AppointmentCase] {

    override def atVersion(at: OffsetDateTime): AppointmentCase = copy(version = at)
    override def storedVersion[B <: StoredVersion[AppointmentCase]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      AppointmentCaseVersion(
        id,
        appointmentID,
        caseID,
        teamMember,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  object AppointmentCase extends Versioning {
    def tupled = (AppointmentCase.apply _).tupled

    sealed trait AppointmentCaseProperties {
      self: Table[_] =>

      def id = column[UUID]("id")
      def appointmentID = column[UUID]("appointment_id")
      def caseID = column[UUID]("case_id")
      def teamMember = column[Usercode]("team_member")
      def version = column[OffsetDateTime]("version_utc")
    }

    class AppointmentCases(tag: Tag) extends Table[AppointmentCase](tag, "appointment_case") with VersionedTable[AppointmentCase] with AppointmentCaseProperties {
      override def matchesPrimaryKey(other: AppointmentCase): Rep[Boolean] = id === other.id
      def pk = primaryKey("pk_appointment_case", id)
      def fkAppointment = foreignKey("fk_appointment_case_appointment", appointmentID, appointments.table)(_.id)
      def fkCase = foreignKey("fk_appointment_case_case", caseID, cases.table)(_.id)
      def * : ProvenShape[AppointmentCase] = (id, appointmentID, caseID, teamMember, version).mapTo[AppointmentCase]
    }

    class AppointmentCaseVersions(tag: Tag) extends Table[AppointmentCaseVersion](tag, "appointment_case_version") with StoredVersionTable[AppointmentCase] with AppointmentCaseProperties {
      def operation = column[DatabaseOperation]("version_operation")
      def timestamp = column[OffsetDateTime]("version_timestamp_utc")
      def auditUser = column[Option[Usercode]]("version_user")
      def * = (id, appointmentID, caseID, teamMember, version, operation, timestamp, auditUser).mapTo[AppointmentCaseVersion]
      def pk = primaryKey("pk_appointment_case_version", (id, timestamp))
    }

    val appointmentCases: VersionedTableQuery[AppointmentCase, AppointmentCaseVersion, AppointmentCases, AppointmentCaseVersions] =
      VersionedTableQuery(TableQuery[AppointmentCases], TableQuery[AppointmentCaseVersions])
  }

  case class AppointmentCaseVersion(
    id: UUID,
    appointmentID: UUID,
    caseID: UUID,
    teamMember: Usercode,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[AppointmentCase]

}