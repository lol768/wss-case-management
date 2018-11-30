package domain.dao

import java.time.{Duration, LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.QueryHelpers._
import domain._
import domain.dao.AppointmentDao.AppointmentCase.AppointmentCases
import domain.dao.AppointmentDao._
import domain.dao.CaseDao.cases
import domain.dao.ClientDao.StoredClient.Clients
import domain.dao.MemberDao.StoredMember.Members
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.core.helpers.JavaTime
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
  def countForClientBadge(universityID: UniversityID): DBIO[Int]

  def casesForAppointmentQuery(appointment: UUID): Query[AppointmentCases, AppointmentCase, Seq]
  def insertCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Seq[AppointmentCase]]
  def deleteCaseLinks(joins: Set[AppointmentCase])(implicit ac: AuditLogContext): DBIO[Done]
  def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]]
  def updateClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient]
  def deleteClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Done]
  def findClientByIDQuery(appointmentID: UUID, universityID: UniversityID): Query[AppointmentClients, StoredAppointmentClient, Seq]
  def findClientsQuery(appointmentIDs: Set[UUID]): Query[AppointmentClients, StoredAppointmentClient, Seq]
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
    def queries(a: Appointments, c: Clients, tm: Members): Seq[Rep[Option[Boolean]]] =
      Seq[Option[Rep[Option[Boolean]]]](
        q.query.filter(_.nonEmpty).map { queryStr =>
          val query = prefixTsQuery(queryStr.bind)

          (a.searchableId @+ a.searchableKey @+ c.searchableUniversityID @+ tm.searchableUsercode @+ c.searchableFullName @+ tm.searchableFullName) @@ query
        },
        q.createdAfter.map { d => a.created.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.createdBefore.map { d => a.created.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.client.map { client => c.universityID.? === client },
        q.startAfter.map { d => a.start.? >= d.atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.startBefore.map { d => a.start.? <= d.plusDays(1).atStartOfDay.atZone(JavaTime.timeZone).toOffsetDateTime },
        q.endAfter.map { d => a.end.? >= d },
        q.endBefore.map { d => a.end.? <= d },
        q.team.map { team => a.team.? === team },
        q.teamMember.map { member => tm.usercode.? === member },
        q.roomID.map { roomID => a.roomID === roomID },
        q.appointmentType.map { appointmentType => a.appointmentType.? === appointmentType },
        q.purpose.map { purpose => a.purpose.? === purpose },
        q.hasOutcome.map { hasOutcome => (a.outcome.length().? > 0) === hasOutcome },
        q.states.headOption.map { _ => a.state.inSet(q.states).? }
      ).flatten

    appointments.table
      .withClientsAndTeamMembers
      .filter { case (a, _, c, _, tm) => queries(a, c, tm).reduce(_ && _) }
      .distinct
      .map { case (a, _, _, _, _) => a }
  }

  override def countForClientBadge(universityID: UniversityID): DBIO[Int] = {
    appointments.table
      .withClients
      .filter { case (appointment, ac, client) =>
        client.universityID === universityID &&
          appointment.start > JavaTime.offsetDateTime &&
          appointment.state =!= (AppointmentState.Cancelled:AppointmentState) &&
          ac.state =!= (AppointmentState.Cancelled:AppointmentState)
      }
      .length
      .result
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
}

object AppointmentDao {
  val appointments: VersionedTableQuery[StoredAppointment, StoredAppointmentVersion, Appointments, AppointmentVersions] =
    VersionedTableQuery(TableQuery[Appointments], TableQuery[AppointmentVersions])

  val appointmentClients: VersionedTableQuery[StoredAppointmentClient, StoredAppointmentClientVersion, AppointmentClients, AppointmentClientVersions] =
    VersionedTableQuery(TableQuery[AppointmentClients], TableQuery[AppointmentClientVersions])

  case class StoredAppointment(
    id: UUID,
    key: IssueKey,
    start: OffsetDateTime,
    duration: Duration,
    roomID: Option[UUID],
    team: Team,
    appointmentType: AppointmentType,
    purpose: AppointmentPurpose,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    outcome: List[String],
    dsaSupportAccessed: Option[AppointmentDSASupportAccessed],
    dsaActionPoints: List[String],
    dsaActionPointOther: Option[String],
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
      purpose,
      state,
      cancellationReason,
      outcome.toSet.map(AppointmentOutcome.withName),
      dsaSupportAccessed,
      AppointmentDSAActionPoint(dsaActionPoints, dsaActionPointOther),
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
        purpose,
        state,
        cancellationReason,
        outcome,
        dsaSupportAccessed,
        dsaActionPoints,
        dsaActionPointOther,
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
    purpose: AppointmentPurpose,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    outcome: List[String],
    dsaSupportAccessed: Option[AppointmentDSASupportAccessed],
    dsaActionPoints: List[String],
    dsaActionPointOther: Option[String],
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
    def purpose = column[AppointmentPurpose]("appointment_purpose")
    def state = column[AppointmentState]("state")
    def cancellationReason = column[Option[AppointmentCancellationReason]]("cancellation_reason")
    def outcome = column[List[String]]("outcome")
    def dsaSupportAccessed = column[Option[AppointmentDSASupportAccessed]]("dsa_support_accessed")
    def dsaActionPoints = column[List[String]]("dsa_action_points")
    def dsaActionPointOther = column[Option[String]]("dsa_action_point_other")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Appointments(tag: Tag) extends Table[StoredAppointment](tag, "appointment")
    with VersionedTable[StoredAppointment]
    with CommonAppointmentProperties {
    override def matchesPrimaryKey(other: StoredAppointment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)
    def searchableId = toTsVector(id.asColumnOf[String], Some("english"))

    override def * : ProvenShape[StoredAppointment] =
      (id, key, start, duration, roomID, team, appointmentType, purpose, state, cancellationReason, outcome, dsaSupportAccessed, dsaActionPoints, dsaActionPointOther, created, version).mapTo[StoredAppointment]

    def end: Rep[OffsetDateTime] =
      SimpleExpression.binary[OffsetDateTime, Duration, OffsetDateTime] { (start, duration, qb) =>
        qb.expr(start)
        qb.sqlBuilder += " + ("
        qb.expr(duration)
        qb.sqlBuilder += " || ' seconds')::interval"
      }.apply(start, duration)

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
      (id, key, start, duration, roomID, team, appointmentType, purpose, state, cancellationReason, outcome, dsaSupportAccessed, dsaActionPoints, dsaActionPointOther, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentVersion]
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
    def withRoom = q
      .join(LocationDao.rooms.table)
      .on(_.roomID === _.id)
      .join(LocationDao.buildings.table)
      .on(_._2.buildingID === _.id)
      .map { case ((a, r), b) => (a, (r.id, b.building, r.name, r.wai2GoID.getOrElse(b.wai2GoID), r.available, r.created, r.version).mapTo[Room]) }
    def withTeamMembers = q
      .join(Owner.owners.table)
      .on { case (a, o) => a.id === o.entityId && o.entityType === (Owner.EntityType.Appointment: Owner.EntityType) }
      .join(MemberDao.members.table)
      .on { case ((_, o), m) => o.userId === m.usercode }
      .flattenJoin
    def withClientsAndTeamMembers = q
      .withClients
      .join(Owner.owners.table)
      .on { case ((a, _, _), o) => a.id === o.entityId && o.entityType === (Owner.EntityType.Appointment: Owner.EntityType) }
      .flattenJoin
      .join(MemberDao.members.table)
      .on { case ((_, _, _, o), m) => o.userId === m.usercode }
      .flattenJoin
  }

  case class StoredAppointmentClient(
    universityID: UniversityID,
    appointmentID: UUID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    attendanceState: Option[AppointmentClientAttendanceState],
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointmentClient] {
    def asAppointmentClient(client: Client) = AppointmentClient(
      client,
      state,
      cancellationReason,
      attendanceState,
    )

    override def atVersion(at: OffsetDateTime): StoredAppointmentClient = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointmentClient]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentClientVersion(
        universityID,
        appointmentID,
        state,
        cancellationReason,
        attendanceState,
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
    attendanceState: Option[AppointmentClientAttendanceState],
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
    def attendanceState = column[Option[AppointmentClientAttendanceState]]("attendance_state")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class AppointmentClients(tag: Tag) extends Table[StoredAppointmentClient](tag, "appointment_client")
    with VersionedTable[StoredAppointmentClient]
    with CommonAppointmentClientProperties {
    override def matchesPrimaryKey(other: StoredAppointmentClient): Rep[Boolean] =
      universityID === other.universityID && appointmentID === other.appointmentID

    override def * : ProvenShape[StoredAppointmentClient] =
      (universityID, appointmentID, state, cancellationReason, attendanceState, created, version).mapTo[StoredAppointmentClient]

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
      (universityID, appointmentID, state, cancellationReason, attendanceState, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentClientVersion]
    def pk = primaryKey("pk_appointment_client_version", (universityID, appointmentID, timestamp))
    def idx = index("idx_appointment_client_version", (universityID, appointmentID, version))
  }

  implicit class AppointmentClientExtensions[C[_]](q: Query[AppointmentClients, StoredAppointmentClient, C]) {
    def withClients = q
      .join(ClientDao.clients.table)
      .on(_.universityID === _.universityID)
  }

  case class AppointmentSearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    client: Option[UniversityID] = None,
    startAfter: Option[LocalDate] = None,
    startBefore: Option[LocalDate] = None,
    endAfter: Option[OffsetDateTime] = None,
    endBefore: Option[OffsetDateTime] = None,
    roomID: Option[UUID] = None,
    team: Option[Team] = None,
    teamMember: Option[Usercode] = None,
    appointmentType: Option[AppointmentType] = None,
    purpose: Option[AppointmentPurpose] = None,
    hasOutcome: Option[Boolean] = None,
    states: Set[AppointmentState] = Set(),
  ) {
    def isEmpty: Boolean = !nonEmpty
    def nonEmpty: Boolean =
      query.exists(_.hasText) ||
      createdAfter.nonEmpty ||
      createdBefore.nonEmpty ||
      client.nonEmpty ||
      startAfter.nonEmpty ||
      startBefore.nonEmpty ||
      endAfter.nonEmpty ||
      endBefore.nonEmpty ||
      team.nonEmpty ||
      teamMember.nonEmpty ||
      roomID.nonEmpty ||
      appointmentType.nonEmpty ||
      purpose.nonEmpty ||
      hasOutcome.nonEmpty ||
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
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[AppointmentCase]

}