package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.CaseDao.{Case, _}
import domain.dao.{CaseDao, DaoRunner}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import play.api.libs.json.Json
import slick.jdbc.PostgresProfile.api._
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CaseServiceImpl])
trait CaseService {
  def create(c: Case, clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def findFull(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]]
  def findFull(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]]
  def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]]
  def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]]
  def addLink(linkType: CaseLinkType, outgoingID: UUID, incomingID: UUID, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[StoredCaseLink]]
  def getLinks(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[(Seq[CaseLink], Seq[CaseLink])]]
  def addNote(caseID: UUID, noteType: CaseNoteType, note: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]]
  def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNote]]]
}

class CaseServiceImpl @Inject() (
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: CaseDao
)(implicit ec: ExecutionContext) extends CaseService {

  override def create(c: Case, clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    require(c.id.isEmpty, "Case must not have an existing ID before being saved")
    require(c.key.isEmpty, "Case must not have an existing key before being saved")

    val id = UUID.randomUUID()
    auditService.audit('CaseSave, id.toString, 'Case, Json.obj()) {
      daoRunner.run(for {
        nextId <- sql"SELECT nextval('SEQ_CASE_ID')".as[Int].head
        inserted <- dao.insert(c.copy(id = Some(id), key = Some(IssueKey(IssueKeyType.Case, nextId))))
        _ <- dao.insertClients(clients.map { universityId => CaseClient(id, universityId) })
      } yield inserted).map(Right.apply)
    }
  }

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(id)).map(Right(_))

  override def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(caseKey)).map(Right(_))

  private def findFullyJoined(find: => DBIO[Case])(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]] =
    daoRunner.run(for {
      clientCase <- find
      clients <- dao.findClientsQuery(Set(clientCase.id.get)).result
      tags <- dao.findTagsQuery(Set(clientCase.id.get)).result
      notes <- getNotesDBIO(clientCase.id.get)
      (outgoingCaseLinks, incomingCaseLinks) <- getLinksDBIO(clientCase.id.get)
    } yield Case.FullyJoined(
      clientCase,
      clients.map(_.client).toSet,
      tags.map(_.caseTag).toSet,
      notes.map(_.asCaseNote),
      outgoingCaseLinks,
      incomingCaseLinks
    )).map(Right(_))

  override def findFull(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]] =
    findFullyJoined(dao.find(id))

  override def findFull(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]] =
    findFullyJoined(dao.find(caseKey))

  override def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]] =
    daoRunner.run(dao.findTagsQuery(caseIds).result)
      .map(_.groupBy(_.caseId).mapValues(_.map(_.caseTag).toSet))
      .map(Right.apply)

  override def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]] =
    auditService.audit('CaseSetTags, caseId.toString, 'Case, Json.toJson(tags)) {
      val existing = dao.findTagsQuery(Set(caseId)).result

      val needsRemoving = existing.map(_.filterNot(e => tags.contains(e.caseTag)))
      val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(dao.deleteTag)))

      val needsAdding = existing.map(e => tags.toSeq.filterNot(e.map(_.caseTag).contains))
      val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(t =>
        dao.insertTag(StoredCaseTag(caseId, t))
      )))

      daoRunner.run(DBIO.seq(removals, additions))
        .map(_ => Right(tags))
    }

  override def addLink(linkType: CaseLinkType, outgoingID: UUID, incomingID: UUID, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[StoredCaseLink]] =
    auditService.audit('CaseLinkSave, outgoingID.toString, 'Case, Json.obj("to" -> incomingID.toString, "note" -> caseNote.text)) {
      daoRunner.run(for {
        link <- dao.insertLink(StoredCaseLink(linkType, outgoingID, incomingID))
        _ <- addNoteDBIO(outgoingID, CaseNoteType.AssociatedCase, caseNote)
        _ <- addNoteDBIO(incomingID, CaseNoteType.AssociatedCase, caseNote)
      } yield link).map(Right.apply)
    }

  private def getLinksDBIO(caseID: UUID): DBIO[(Seq[CaseLink], Seq[CaseLink])] =
    dao.findLinksQuery(caseID)
      .join(CaseDao.cases.table).on(_.outgoingCaseID === _.id)
      .join(CaseDao.cases.table).on(_._1.incomingCaseID === _.id)
      .result
      .map { results =>
        results.map { case ((link, outgoing), incoming) =>
          CaseLink(link.linkType, outgoing, incoming, link.version)
        }.partition(_.outgoing.id.get == caseID)
      }

  override def getLinks(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[(Seq[CaseLink], Seq[CaseLink])]] =
    daoRunner.run(getLinksDBIO(caseID)).map(Right.apply)

  private def addNoteDBIO(caseID: UUID, noteType: CaseNoteType, note: CaseNoteSave): DBIO[StoredCaseNote] =
    dao.insertNote(
      StoredCaseNote(
        id = UUID.randomUUID(),
        caseId = caseID,
        noteType = noteType,
        text = note.text,
        teamMember = note.teamMember,
        created = JavaTime.offsetDateTime,
        version = JavaTime.offsetDateTime
      )
    )

  override def addNote(caseID: UUID, noteType: CaseNoteType, note: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]] =
    auditService.audit('CaseAddNote, caseID.toString, 'Case, Json.obj("noteType" -> noteType)) {
      daoRunner.run(addNoteDBIO(caseID, noteType, note)).map { n => Right(n.asCaseNote) }
    }

  private def getNotesDBIO(caseID: UUID): DBIO[Seq[StoredCaseNote]] =
    dao.findNotesQuery(caseID).sortBy(_.created.desc).result

  override def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNote]]] =
    daoRunner.run(getNotesDBIO(caseID)).map { notes => Right(notes.map(_.asCaseNote)) }
}
