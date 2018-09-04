package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import domain.CustomJdbcTypes._
import domain.IssueState._
import domain.dao.CaseDao._
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[CaseDaoImpl])
trait CaseDao {
  def find(id: UUID): DBIO[Case]
  def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag]
  def deleteTag(tag: StoredCaseTag): DBIO[Done]
  def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq]
}

@Singleton
class CaseDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CaseDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def find(id: UUID): DBIO[Case] =
    cases.table.filter(_.id === id).result.head

  override def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag] =
    caseTags.insert(tag)

  override def deleteTag(tag: StoredCaseTag): DBIO[Done] =
    caseTags.delete(tag)

  override def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq] =
    caseTags.table
      .filter(_.caseId.inSet(caseIds))
}

object CaseDao {

  val cases: VersionedTableQuery[Case, CaseVersion, Cases, CaseVersions] =
    VersionedTableQuery(TableQuery[Cases], TableQuery[CaseVersions])

  val caseTags: VersionedTableQuery[StoredCaseTag, StoredCaseTagVersion, CaseTags, CaseTagVersions] =
    VersionedTableQuery(TableQuery[CaseTags], TableQuery[CaseTagVersions])

  case class Case(
    id: Option[UUID],
    created: OffsetDateTime,
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Option[Boolean],
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause
  ) extends Versioned[Case] {
    override def atVersion(at: OffsetDateTime): Case = copy(version = at)
    override def storedVersion[B <: StoredVersion[Case]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      CaseVersion(
        id.get,
        created,
        incidentDate,
        team,
        version,
        state,
        onCampus,
        originalEnquiry,
        caseType,
        cause,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class CaseVersion(
    id: UUID,
    created: OffsetDateTime,
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Option[Boolean],
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
  ) extends StoredVersion[Case]

  trait CommonProperties { self: Table[_] =>
    def created = column[OffsetDateTime]("created_utc")
    def incidentDate = column[OffsetDateTime]("incident_date_utc")
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def state = column[IssueState]("state")
    def onCampus = column[Option[Boolean]]("on_campus")
    def originalEnquiry = column[Option[UUID]]("enquiry_id")
    def caseType = column[Option[CaseType]]("case_type")
    def cause = column[CaseCause]("cause")
  }

  class Cases(tag: Tag) extends Table[Case](tag, "client_case")
    with VersionedTable[Case]
    with CommonProperties {
    override def matchesPrimaryKey(other: Case): Rep[Boolean] = id === other.id.orNull
    def id = column[UUID]("id", O.PrimaryKey/*, O.AutoInc*/)

    def isOpen = state === (Open : IssueState) || state === (Reopened : IssueState)

    override def * : ProvenShape[Case] =
      (id.?, created, incidentDate, team, version, state, onCampus, originalEnquiry, caseType, cause).mapTo[Case]
  }

  class CaseVersions(tag: Tag) extends Table[CaseVersion](tag, "client_case_version")
    with StoredVersionTable[Case]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[CaseVersion] =
      (id, created, incidentDate, team, version, state, onCampus, originalEnquiry, caseType, cause, operation, timestamp).mapTo[CaseVersion]
  }

  case class StoredCaseTag(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[StoredCaseTag] {
    override def atVersion(at: OffsetDateTime): StoredCaseTag = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredCaseTag]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseTagVersion(
        caseId,
        caseTag,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class StoredCaseTagVersion(
    caseId: UUID,
    caseTag: CaseTag,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[StoredCaseTag]

  trait CommonTagProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def caseTag = column[CaseTag]("tag")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseTags(tag: Tag) extends Table[StoredCaseTag](tag, "client_case_tag")
    with VersionedTable[StoredCaseTag]
    with CommonTagProperties {
    override def matchesPrimaryKey(other: StoredCaseTag): Rep[Boolean] =
      caseId === other.caseId && caseTag === other.caseTag

    override def * : ProvenShape[StoredCaseTag] =
      (caseId, caseTag, version).mapTo[StoredCaseTag]
    def pk = primaryKey("pk_case_tag", (caseId, caseTag))
    def fk = foreignKey("fk_case_tag", caseId, cases.table)(_.id)
    def idx = index("idx_case_tag", caseId)
  }

  class CaseTagVersions(tag: Tag) extends Table[StoredCaseTagVersion](tag, "client_case_tag_version")
    with StoredVersionTable[StoredCaseTag]
    with CommonTagProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[StoredCaseTagVersion] =
      (caseId, caseTag, version, operation, timestamp).mapTo[StoredCaseTagVersion]
    def pk = primaryKey("pk_case_tag_version", (caseId, caseTag, timestamp))
    def idx = index("idx_case_tag_version", (caseId, caseTag, version))
  }
}
