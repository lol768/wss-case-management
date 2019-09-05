package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.ClientConsultationDao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ClientConsultationDaoImpl])
trait ClientConsultationDao {
  def insert(consultation: StoredClientConsultation)(implicit ac: AuditLogContext): DBIO[StoredClientConsultation]
  def update(consultation: StoredClientConsultation, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClientConsultation]

  def findSnippetsQuery: Query[ClientConsultations, StoredClientConsultation, Seq]
}

@Singleton
class ClientConsultationDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends ClientConsultationDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  override def insert(clientConsultation: StoredClientConsultation)(implicit ac: AuditLogContext): DBIO[StoredClientConsultation] =
    clientConsultations += clientConsultation

  override def update(clientConsultation: StoredClientConsultation, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredClientConsultation] =
    clientConsultations.update(clientConsultation.copy(version = version))

  override def findSnippetsQuery: Query[ClientConsultations, StoredClientConsultation, Seq] =
    clientConsultations.table
}

object ClientConsultationDao {
  val clientConsultations: VersionedTableQuery[StoredClientConsultation, StoredClientConsultationVersion, ClientConsultations, ClientConsultationVersions] =
    VersionedTableQuery(TableQuery[ClientConsultations], TableQuery[ClientConsultationVersions])

  case class StoredClientConsultation(
    id: UUID,
    universityID: UniversityID,
    reason: String,
    suggestedResolution: String,
    alreadyTried: String,
    sessionFeedback: String,
    administratorOutcomes: String,
    created: OffsetDateTime,
    lastUpdatedBy: Usercode,
    version: OffsetDateTime,
  ) extends Versioned[StoredClientConsultation] {
    override def atVersion(at: OffsetDateTime): StoredClientConsultation = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredClientConsultation]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredClientConsultationVersion(
        id,
        universityID,
        reason,
        suggestedResolution,
        alreadyTried,
        sessionFeedback,
        administratorOutcomes,
        created,
        lastUpdatedBy,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredClientConsultationVersion(
    id: UUID,
    universityID: UniversityID,
    reason: String,
    suggestedResolution: String,
    alreadyTried: String,
    sessionFeedback: String,
    administratorOutcomes: String,
    created: OffsetDateTime,
    lastUpdatedBy: Usercode,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode],
  ) extends StoredVersion[StoredClientConsultation]

  trait CommonClientConsultationProperties { self: Table[_] =>
    def universityID: Rep[UniversityID] = column[UniversityID]("university_id")
    def reason: Rep[String] = column[String]("reason")
    def suggestedResolution: Rep[String] = column[String]("suggested_resolution")
    def alreadyTried: Rep[String] = column[String]("already_tried")
    def sessionFeedback: Rep[String] = column[String]("session_feedback")
    def administratorOutcomes: Rep[String] = column[String]("administrator_outcomes")
    def created: Rep[OffsetDateTime] = column[OffsetDateTime]("created_utc")
    def lastUpdatedBy: Rep[Usercode] = column[Usercode]("last_updated_by")
    def version: Rep[OffsetDateTime] = column[OffsetDateTime]("version_utc")
  }

  class ClientConsultations(tag: Tag) extends Table[StoredClientConsultation](tag, "client_consultation")
    with VersionedTable[StoredClientConsultation]
    with CommonClientConsultationProperties {

    def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

    override def matchesPrimaryKey(other: StoredClientConsultation): Rep[Boolean] = id === other.id

    override def * : ProvenShape[StoredClientConsultation] =
      (id, universityID, reason, suggestedResolution, alreadyTried, sessionFeedback, administratorOutcomes, created, lastUpdatedBy, version)
        .mapTo[StoredClientConsultation]

    def clientConsultation: ProvenShape[ClientConsultation] =
      (id, universityID, reason, suggestedResolution, alreadyTried, sessionFeedback, administratorOutcomes, created, version, lastUpdatedBy)
        .mapTo[ClientConsultation]
    def idx: Index = index("idx_client_consultation", universityID)
  }

  class ClientConsultationVersions(tag: Tag) extends Table[StoredClientConsultationVersion](tag, "client_consultation_version")
    with StoredVersionTable[StoredClientConsultation]
    with CommonClientConsultationProperties {

    def id: Rep[UUID] = column[UUID]("id")
    def operation: Rep[DatabaseOperation] = column[DatabaseOperation]("version_operation")
    def timestamp: Rep[OffsetDateTime] = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser: Rep[Option[Usercode]] = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredClientConsultationVersion] =
      (id, universityID, reason, suggestedResolution, alreadyTried, sessionFeedback, administratorOutcomes, created, lastUpdatedBy, version, operation, timestamp, auditUser)
        .mapTo[StoredClientConsultationVersion]

    def pk: PrimaryKey = primaryKey("pk_client_consultation_version", (id, timestamp))
    def idx: Index = index("idx_client_consultation_version", (id, version))
  }
}
