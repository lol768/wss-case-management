package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.IssueState._
import domain._
import domain.dao.CaseDao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[CaseDaoImpl])
trait CaseDao {
  def insert(c: Case): DBIO[Case]
  def find(id: UUID): DBIO[Case]
  def find(key: IssueKey): DBIO[Case]
  def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag]
  def deleteTag(tag: StoredCaseTag): DBIO[Done]
  def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq]
  def insertClients(clients: Set[CaseClient]): DBIO[Seq[CaseClient]]
  def insertClient(client: CaseClient): DBIO[CaseClient]
  def deleteClient(client: CaseClient): DBIO[Done]
  def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq]
  def insertLink(link: StoredCaseLink): DBIO[StoredCaseLink]
  def deleteLink(link: StoredCaseLink): DBIO[Done]
  def findLinks(caseID: UUID): DBIO[(Seq[CaseLink], Seq[CaseLink])] // (Outgoing, Incoming)
}

@Singleton
class CaseDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CaseDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(c: Case): DBIO[Case] =
    cases.insert(c)

  override def find(id: UUID): DBIO[Case] =
    cases.table.filter(_.id === id).result.head

  override def find(key: IssueKey): DBIO[Case] =
    cases.table.filter(_.key === key).result.head

  override def insertTag(tag: StoredCaseTag): DBIO[StoredCaseTag] =
    caseTags.insert(tag)

  override def deleteTag(tag: StoredCaseTag): DBIO[Done] =
    caseTags.delete(tag)

  override def findTagsQuery(caseIds: Set[UUID]): Query[CaseTags, StoredCaseTag, Seq] =
    caseTags.table
      .filter(_.caseId.inSet(caseIds))

  override def insertClients(clients: Set[CaseClient]): DBIO[Seq[CaseClient]] =
    caseClients.insertAll(clients.toSeq)

  override def insertClient(client: CaseClient): DBIO[CaseClient] =
    caseClients.insert(client)

  override def deleteClient(client: CaseClient): DBIO[Done] =
    caseClients.delete(client)

  override def findClientsQuery(caseIds: Set[UUID]): Query[CaseClients, CaseClient, Seq] =
    caseClients.table
      .filter(_.caseId.inSet(caseIds))

  override def insertLink(link: StoredCaseLink): DBIO[StoredCaseLink] =
    caseLinks.insert(link)

  override def deleteLink(link: StoredCaseLink): DBIO[Done] =
    caseLinks.delete(link)

  override def findLinks(caseID: UUID): DBIO[(Seq[CaseLink], Seq[CaseLink])] =
    caseLinks.table
      .join(cases.table).on(_.outgoingCaseID === _.id)
      .join(cases.table).on(_._1.incomingCaseID === _.id)
      .filter { case ((l, _), _) => l.outgoingCaseID === caseID || l.incomingCaseID === caseID }
      .result
      .map { results =>
        results.map { case ((link, outgoing), incoming) =>
          CaseLink(link.linkType, outgoing, incoming, link.version)
        }.partition(_.outgoing.id.get == caseID)
      }
}

object CaseDao {

  val cases: VersionedTableQuery[Case, CaseVersion, Cases, CaseVersions] =
    VersionedTableQuery(TableQuery[Cases], TableQuery[CaseVersions])

  val caseTags: VersionedTableQuery[StoredCaseTag, StoredCaseTagVersion, CaseTags, CaseTagVersions] =
    VersionedTableQuery(TableQuery[CaseTags], TableQuery[CaseTagVersions])

  val caseClients: VersionedTableQuery[CaseClient, CaseClientVersion, CaseClients, CaseClientVersions] =
    VersionedTableQuery(TableQuery[CaseClients], TableQuery[CaseClientVersions])

  val caseLinks: VersionedTableQuery[StoredCaseLink, StoredCaseLinkVersion, CaseLinks, CaseLinkVersions] =
    VersionedTableQuery(TableQuery[CaseLinks], TableQuery[CaseLinkVersions])

  case class Case(
    id: Option[UUID],
    key: Option[IssueKey],
    created: OffsetDateTime,
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause
  ) extends Versioned[Case] {
    override def atVersion(at: OffsetDateTime): Case = copy(version = at)
    override def storedVersion[B <: StoredVersion[Case]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      CaseVersion(
        id.get,
        key.get,
        created,
        incidentDate,
        team,
        version,
        state,
        onCampus,
        notifiedPolice,
        notifiedAmbulance,
        notifiedFire,
        originalEnquiry,
        caseType,
        cause,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  object Case {
    def tupled = (apply _).tupled

    /**
      * This might not be a way we should do things, but if we did want a service to return
      * everything we need to display
      */
    case class FullyJoined(
      clientCase: Case,
      clients: Set[UniversityID],
      tags: Set[CaseTag],
      //    notes: Seq[CaseNote],
      //    attachments: Seq[UploadedDocument],
      //    relatedAppointments: Seq[Appointment],
      outgoingCaseLinks: Seq[CaseLink],
      incomingCaseLinks: Seq[CaseLink]
    )
  }

  case class CaseVersion(
    id: UUID,
    key: IssueKey,
    created: OffsetDateTime,
    incidentDate: OffsetDateTime,
    team: Team,
    version: OffsetDateTime,
    state: IssueState,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    originalEnquiry: Option[UUID],
    caseType: Option[CaseType],
    cause: CaseCause,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
  ) extends StoredVersion[Case]

  trait CommonProperties { self: Table[_] =>
    def key = column[IssueKey]("case_key")
    def created = column[OffsetDateTime]("created_utc")
    def incidentDate = column[OffsetDateTime]("incident_date_utc")
    def team = column[Team]("team_id")
    def version = column[OffsetDateTime]("version_utc")
    def state = column[IssueState]("state")
    def onCampus = column[Boolean]("on_campus")
    def notifiedPolice = column[Boolean]("notified_police")
    def notifiedAmbulance = column[Boolean]("notified_ambulance")
    def notifiedFire = column[Boolean]("notified_fire")
    def originalEnquiry = column[Option[UUID]]("enquiry_id")
    def caseType = column[Option[CaseType]]("case_type")
    def cause = column[CaseCause]("cause")
  }

  class Cases(tag: Tag) extends Table[Case](tag, "client_case")
    with VersionedTable[Case]
    with CommonProperties {
    override def matchesPrimaryKey(other: Case): Rep[Boolean] = id === other.id.orNull
    def id = column[UUID]("id", O.PrimaryKey)

    def isOpen = state === (Open : IssueState) || state === (Reopened : IssueState)

    override def * : ProvenShape[Case] =
      (id.?, key.?, created, incidentDate, team, version, state, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause).mapTo[Case]
    def idx = index("idx_client_case_key", key, unique = true)
  }

  class CaseVersions(tag: Tag) extends Table[CaseVersion](tag, "client_case_version")
    with StoredVersionTable[Case]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[CaseVersion] =
      (id, key, created, incidentDate, team, version, state, onCampus, notifiedPolice, notifiedAmbulance, notifiedFire, originalEnquiry, caseType, cause, operation, timestamp).mapTo[CaseVersion]
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

  case class CaseClient(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[CaseClient] {
    override def atVersion(at: OffsetDateTime): CaseClient = copy(version = at)
    override def storedVersion[B <: StoredVersion[CaseClient]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      CaseClientVersion(
        caseId,
        client,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class CaseClientVersion(
    caseId: UUID,
    client: UniversityID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[CaseClient]

  trait CommonClientProperties { self: Table[_] =>
    def caseId = column[UUID]("case_id")
    def client = column[UniversityID]("university_id")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseClients(tag: Tag) extends Table[CaseClient](tag, "client_case_client")
    with VersionedTable[CaseClient]
    with CommonClientProperties {
    override def matchesPrimaryKey(other: CaseClient): Rep[Boolean] =
      caseId === other.caseId && client === other.client

    override def * : ProvenShape[CaseClient] =
      (caseId, client, version).mapTo[CaseClient]
    def pk = primaryKey("pk_case_client", (caseId, client))
    def fk = foreignKey("fk_case_client", caseId, cases.table)(_.id)
    def caseIndex = index("idx_case_client", caseId)
    def clientIndex = index("idx_case_client_university_id", client)
  }

  class CaseClientVersions(tag: Tag) extends Table[CaseClientVersion](tag, "client_case_client_version")
    with StoredVersionTable[CaseClient]
    with CommonClientProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[CaseClientVersion] =
      (caseId, client, version, operation, timestamp).mapTo[CaseClientVersion]
    def pk = primaryKey("pk_case_client_version", (caseId, client, timestamp))
    def idx = index("idx_case_client_version", (caseId, client, version))
  }

  case class StoredCaseLink(
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    version: OffsetDateTime = OffsetDateTime.now()
  ) extends Versioned[StoredCaseLink] {
    override def atVersion(at: OffsetDateTime): StoredCaseLink = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredCaseLink]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      StoredCaseLinkVersion(
        linkType,
        outgoingCaseID,
        incomingCaseID,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]
  }

  case class StoredCaseLinkVersion(
    linkType: CaseLinkType,
    outgoingCaseID: UUID,
    incomingCaseID: UUID,
    version: OffsetDateTime = OffsetDateTime.now(),
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[StoredCaseLink]

  trait CommonLinkProperties { self: Table[_] =>
    def linkType = column[CaseLinkType]("link_type")
    def outgoingCaseID = column[UUID]("outgoing_case_id")
    def incomingCaseID = column[UUID]("incoming_case_id")
    def version = column[OffsetDateTime]("version_utc")
  }

  class CaseLinks(tag: Tag) extends Table[StoredCaseLink](tag, "client_case_link")
    with VersionedTable[StoredCaseLink]
    with CommonLinkProperties {
    override def matchesPrimaryKey(other: StoredCaseLink): Rep[Boolean] =
      linkType === other.linkType && outgoingCaseID === other.outgoingCaseID && incomingCaseID === other.incomingCaseID

    override def * : ProvenShape[StoredCaseLink] =
      (linkType, outgoingCaseID, incomingCaseID, version).mapTo[StoredCaseLink]
    def pk = primaryKey("pk_case_link", (linkType, outgoingCaseID, incomingCaseID))
    def outgoingFK = foreignKey("fk_case_link_outgoing", outgoingCaseID, cases.table)(_.id)
    def incomingFK = foreignKey("fk_case_link_incoming", incomingCaseID, cases.table)(_.id)
    def outgoingCaseIndex = index("idx_case_link_outgoing", outgoingCaseID)
    def incomingCaseIndex = index("idx_case_link_incoming", incomingCaseID)
  }

  class CaseLinkVersions(tag: Tag) extends Table[StoredCaseLinkVersion](tag, "client_case_link_version")
    with StoredVersionTable[StoredCaseLink]
    with CommonLinkProperties {
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")

    override def * : ProvenShape[StoredCaseLinkVersion] =
      (linkType, outgoingCaseID, incomingCaseID, version, operation, timestamp).mapTo[StoredCaseLinkVersion]
    def pk = primaryKey("pk_case_link_version", (linkType, outgoingCaseID, incomingCaseID, timestamp))
    def idx = index("idx_case_link_version", (linkType, outgoingCaseID, incomingCaseID, version))
  }
}
