package domain.dao
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao._
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext
import java.time.Duration

import services.AuditLogContext

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
  def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]]
  def insertClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient]
  def deleteClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[Done]
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
      .filter { case (_, client) => client.universityID === universityID }
      .map { case (a, _) => a }

  override def findByCaseQuery(caseID: UUID): Query[Appointments, StoredAppointment, Seq] =
    appointments.table.filter(_.caseID === caseID)

  override def insertClients(clients: Set[StoredAppointmentClient])(implicit ac: AuditLogContext): DBIO[Seq[StoredAppointmentClient]] =
    appointmentClients.insertAll(clients.toSeq)

  override def insertClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[StoredAppointmentClient] =
    appointmentClients.insert(client)

  override def deleteClient(client: StoredAppointmentClient)(implicit ac: AuditLogContext): DBIO[Done] =
    appointmentClients.delete(client)

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
    caseID: Option[UUID],
    subject: String,
    start: OffsetDateTime,
    duration: Duration,
    location: Option[Location],
    team: Team,
    teamMember: Usercode,
    appointmentType: AppointmentType,
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointment] {
    def asAppointment = Appointment(
      id,
      key,
      subject,
      start,
      duration,
      location,
      team,
      teamMember,
      appointmentType,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredAppointment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAppointment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAppointmentVersion(
        id,
        key,
        caseID,
        subject,
        start,
        duration,
        location,
        team,
        teamMember,
        appointmentType,
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
    subject: String,
    start: OffsetDateTime,
    duration: Duration,
    location: Option[Location],
    team: Team,
    teamMember: Usercode,
    appointmentType: AppointmentType,
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
    def subject = column[String]("subject")
    def searchableSubject = toTsVector(subject, Some("english"))
    def start = column[OffsetDateTime]("start_utc")
    def duration = column[Duration]("duration_secs")
    def location = column[Option[Location]]("location")
    def team = column[Team]("team_id")
    def teamMember = column[Usercode]("team_member")
    def appointmentType = column[AppointmentType]("appointment_type")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Appointments(tag: Tag) extends Table[StoredAppointment](tag, "appointment")
    with VersionedTable[StoredAppointment]
    with CommonAppointmentProperties {
    override def matchesPrimaryKey(other: StoredAppointment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAppointment] =
      (id, key, caseID, subject, start, duration, location, team, teamMember, appointmentType, created, version).mapTo[StoredAppointment]
    def appointment =
      (id, key, subject, start, duration, location, team, teamMember, appointmentType, created, version).mapTo[Appointment]

    def keyIndex = index("idx_appointment_key", key, unique = true)
    def teamIndex = index("idx_appointment_team", (start, team))
    def teamMemberIndex = index("idx_appointment_team_member", (start, teamMember))
    def caseIndex = index("idx_appointment_case", caseID)
    def caseFK = foreignKey("fk_appointment_case", caseID, CaseDao.cases.table)(_.id.?)
  }

  class AppointmentVersions(tag: Tag) extends Table[StoredAppointmentVersion](tag, "appointment_version")
    with StoredVersionTable[StoredAppointment]
    with CommonAppointmentProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAppointmentVersion] =
      (id, key, caseID, subject, start, duration, location, team, teamMember, appointmentType, created, version, operation, timestamp, auditUser).mapTo[StoredAppointmentVersion]
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
  }

  case class StoredAppointmentClient(
    universityID: UniversityID,
    appointmentID: UUID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAppointmentClient] {
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

  case class AppointmentSearchQuery(
    query: Option[String] = None,
    createdAfter: Option[LocalDate] = None,
    createdBefore: Option[LocalDate] = None,
    startAfter: Option[LocalDate] = None,
    startBefore: Option[LocalDate] = None,
    location: Option[Location] = None,
    team: Option[Team] = None,
    teamMember: Option[Usercode] = None,
    appointmentType: Option[AppointmentType] = None
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
      appointmentType.nonEmpty
  }
}