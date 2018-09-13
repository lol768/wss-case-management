package services

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import domain.dao.CaseDao.{Case, _}
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{CaseDao, DaoRunner, UploadedFileDao}
import helpers.JavaTime
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.CaseService._
import domain.ExtendedPostgresProfile.api._
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[CaseServiceImpl])
trait CaseService {
  def create(c: Case, clients: Set[UniversityID], tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def findFull(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Case.FullyJoined]]
  def findFull(caseKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Case.FullyJoined]]
  def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, Seq[MessageData], Seq[CaseNote])]]]
  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def search(query: CaseSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def update(c: Case, clients: Set[UniversityID], tags: Set[CaseTag], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def updateState(caseID: UUID, targetState: IssueState, version: OffsetDateTime, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]]
  def getCaseTags(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[CaseTag]]]
  def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]]
  def addLink(linkType: CaseLinkType, outgoingID: UUID, incomingID: UUID, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[StoredCaseLink]]
  def getLinks(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[(Seq[CaseLink], Seq[CaseLink])]]
  def addGeneralNote(caseID: UUID, note: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]]
  def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNote]]]
  def updateNote(caseID: UUID, noteID: UUID, note: CaseNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]]
  def deleteNote(caseID: UUID, noteID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def listOpenCases(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]]
  def listOpenCases(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]]
  def listClosedCases(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]]
  def listClosedCases(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]]
  def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]
  def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]
  def getClients(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[UniversityID]]]]
  def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[UniversityID]]]
  def addDocument(caseID: UUID, document: CaseDocumentSave, in: ByteSource, file: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseDocument]]
  def getDocuments(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseDocument]]]
  def deleteDocument(caseID: UUID, documentID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def reassign(c: Case, team: Team, caseType: Option[CaseType], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
}

@Singleton
class CaseServiceImpl @Inject() (
  auditService: AuditService,
  ownerService: OwnerService,
  uploadedFileService: UploadedFileService,
  notificationService: NotificationService,
  daoRunner: DaoRunner,
  dao: CaseDao
)(implicit ec: ExecutionContext) extends CaseService {

  override def create(c: Case, clients: Set[UniversityID], tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    require(c.id.isEmpty, "Case must not have an existing ID before being saved")
    require(c.key.isEmpty, "Case must not have an existing key before being saved")

    val id = UUID.randomUUID()
    auditService.audit('CaseSave, id.toString, 'Case, Json.obj()) {
      daoRunner.run(for {
        nextId <- sql"SELECT nextval('SEQ_CASE_ID')".as[Int].head
        inserted <- dao.insert(c.copy(id = Some(id), key = Some(IssueKey(IssueKeyType.Case, nextId))))
        _ <- dao.insertClients(clients.map { universityId => CaseClient(id, universityId) })
        _ <- dao.insertTags(tags.map { t => StoredCaseTag(id, t) })
      } yield inserted).map(Right.apply)
    }
  }

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(id)).map(Right(_))

  def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    daoRunner.run(dao.find(ids.toSet)).map { cases =>
      val lookup = cases.groupBy(_.id.get).mapValues(_.head)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find a Case with ID $id") })
    }

  override def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(caseKey)).map(Right(_))

  private def findFullyJoined(query: Query[Cases, Case, Seq])(implicit t: TimingContext): Future[ServiceResult[Case.FullyJoined]] =
    daoRunner.run(for {
      (clientCase, messages) <-
        query.withMessages
          .sortBy { case (_, m) => m.map(_.created) }
          .map { case (c, m) => (c, m.map(_.messageData)) }
          .result
          .map { results => OneToMany.leftJoin(results)(MessageData.dateOrdering).head }
      clients <- dao.findClientsQuery(Set(clientCase.id.get)).result
      tags <- dao.findTagsQuery(Set(clientCase.id.get)).result
      notes <- getNotesDBIO(clientCase.id.get)
      docs <- getDocumentsDBIO(clientCase.id.get)
      (outgoingCaseLinks, incomingCaseLinks) <- getLinksDBIO(clientCase.id.get)
    } yield Case.FullyJoined(
      clientCase,
      clients.map(_.client).toSet,
      tags.map(_.caseTag).toSet,
      notes.map(_.asCaseNote),
      docs.map { case (d, f) => d.asCaseDocument(f.asUploadedFile) },
      outgoingCaseLinks,
      incomingCaseLinks,
      messages
    )).map(Right(_))

  override def findFull(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Case.FullyJoined]] =
    auditService.audit('CaseView, id.toString, 'Case, Json.obj()) {
      findFullyJoined(dao.findByIDQuery(id))
    }

  override def findFull(caseKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Case.FullyJoined]] =
    auditService.audit('CaseView, (c: Case.FullyJoined) => c.clientCase.id.get.toString, 'Case, Json.obj()) {
      findFullyJoined(dao.findByKeyQuery(caseKey))
    }

  override def findForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, Seq[MessageData], Seq[CaseNote])]]] =
    daoRunner.run(
      dao.findByClientQuery(universityID)
        .withMessagesAndNotes
        .sortBy { case (c, m, n) => (c.version.reverse, m.map(_.created), n.map(_.created)) }
        .map { case (c, m, n) => (c, m.map(_.messageData), n) }
        .result
    ).map { tuples => // Seq[(Case, Option[MessageData], Option[StoredCaseNote])]
      Right(groupTuples(tuples))
    }

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    auditService.findRecentTargetIDsByOperation('CaseView, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => find(ids.map(UUID.fromString))
    ))

  override def search(query: CaseSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    daoRunner.run(dao.searchQuery(query).take(limit).result).map(Right.apply)

  private def updateDifferencesDBIO[A, B](items: Set[B], query: Query[Table[A], A, Seq], map: A => B, comap: B => A, insert: A => DBIO[A], delete: A => DBIO[Done]): DBIO[Unit] = {
    val existing = query.result

    val needsRemoving = existing.map(_.filterNot(e => items.contains(map(e))))
    val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(delete)))

    val needsAdding = existing.map(e => items.toSeq.filterNot(e.map(map).contains))
    val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(comap).map(insert)))

    DBIO.seq(removals, additions)
  }

  override def update(c: Case, clients: Set[UniversityID], tags: Set[CaseTag], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    auditService.audit('CaseUpdate, c.id.get.toString, 'Case, Json.obj()) {
      daoRunner.run(for {
        updated <- dao.update(c, version)
        _ <- updateDifferencesDBIO[CaseClient, UniversityID](
          clients,
          dao.findClientsQuery(Set(c.id.get)),
          _.client,
          id => CaseClient(c.id.get, id),
          dao.insertClient,
          dao.deleteClient
        )
        _ <- updateDifferencesDBIO[StoredCaseTag, CaseTag](
          tags,
          dao.findTagsQuery(Set(c.id.get)),
          _.caseTag,
          t => StoredCaseTag(c.id.get, t),
          dao.insertTag,
          dao.deleteTag
        )
      } yield updated).map(Right.apply)
    }
  }

  override def updateState(caseID: UUID, targetState: IssueState, version: OffsetDateTime, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    val noteType = targetState match {
      case IssueState.Closed => CaseNoteType.CaseClosed
      case IssueState.Reopened => CaseNoteType.CaseReopened
      case _ => throw new IllegalArgumentException(s"Invalid target state $targetState")
    }

    auditService.audit(Symbol(s"Case${targetState.entryName}"), caseID.toString, 'Case, Json.obj()) {
      daoRunner.run(for {
        clientCase <- dao.find(caseID)
        updated <- dao.update(clientCase.copy(state = targetState), version)
        _ <- addNoteDBIO(caseID, noteType, caseNote)
      } yield updated).map(Right.apply)
    }
  }

  override def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]] =
    daoRunner.run(dao.findTagsQuery(caseIds).result)
      .map(_.groupBy(_.caseId).mapValues(_.map(_.caseTag).toSet))
      .map(Right.apply)

  override def getCaseTags(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[CaseTag]]] = {
    getCaseTags(Set(id)).map(_.right.map(_.getOrElse(id, Set.empty)))
  }

  override def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]] =
    auditService.audit('CaseSetTags, caseId.toString, 'Case, Json.toJson(tags)) {
      daoRunner.run(updateDifferencesDBIO[StoredCaseTag, CaseTag](
        tags,
        dao.findTagsQuery(Set(caseId)),
        _.caseTag,
        t => StoredCaseTag(caseId, t),
        dao.insertTag,
        dao.deleteTag
      )).map(_ => Right(tags))
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

  override def addGeneralNote(caseID: UUID, note: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]] =
    auditService.audit('CaseAddGeneralNote, caseID.toString, 'Case, Json.obj()) {
      daoRunner.run(addNoteDBIO(caseID, CaseNoteType.GeneralNote, note)).map { n => Right(n.asCaseNote) }
    }

  private def getNotesDBIO(caseID: UUID): DBIO[Seq[StoredCaseNote]] =
    dao.findNotesQuery(caseID).sortBy(_.created.desc).result

  override def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNote]]] =
    daoRunner.run(getNotesDBIO(caseID)).map { notes => Right(notes.map(_.asCaseNote)) }

  override def updateNote(caseID: UUID, noteID: UUID, note: CaseNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]] =
    auditService.audit('CaseNoteUpdate, caseID.toString, 'Case, Json.obj("noteID" -> noteID.toString)) {
      daoRunner.run(for {
        existing <- dao.findNotesQuery(caseID).filter(_.id === noteID).result.head
        updated <- dao.updateNote(existing.copy(text = note.text, teamMember = note.teamMember), version)
      } yield updated).map { n => Right(n.asCaseNote) }
    }

  override def deleteNote(caseID: UUID, noteID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit('CaseNoteDelete, caseID.toString, 'Case, Json.obj("noteID" -> noteID.toString)) {
      daoRunner.run(for {
        existing <- dao.findNotesQuery(caseID).filter(_.id === noteID).result.head
        done <- dao.deleteNote(existing, version)
      } yield done).map(Right.apply)
    }

  override def listOpenCases(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]] =
    daoRunner.run(
      dao.listQuery(Some(team), None, IssueStateFilter.Open)
        .withLastUpdated
        .result
        .map(_.map { case (c, messageLastUpdated, noteLastUpdated) =>
          (c, Seq(Option(c.version), messageLastUpdated, noteLastUpdated).flatten.max)
        })
    ).map(Right.apply)

  override def listOpenCases(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]] =
    daoRunner.run(
      dao.listQuery(None, Some(owner), IssueStateFilter.Open)
        .withLastUpdated
        .result
        .map(_.map { case (c, messageLastUpdated, noteLastUpdated) =>
          (c, Seq(Option(c.version), messageLastUpdated, noteLastUpdated).flatten.max)
        })
    ).map(Right.apply)

  override def listClosedCases(team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]] =
    daoRunner.run(
      dao.listQuery(Some(team), None, IssueStateFilter.Closed)
        .withLastUpdated
        .result
        .map(_.map { case (c, messageLastUpdated, noteLastUpdated) =>
          (c, Seq(Option(c.version), messageLastUpdated, noteLastUpdated).flatten.max)
        })
    ).map(Right.apply)

  override def listClosedCases(owner: Usercode)(implicit t: TimingContext): Future[ServiceResult[Seq[(Case, OffsetDateTime)]]] =
    daoRunner.run(
      dao.listQuery(None, Some(owner), IssueStateFilter.Closed)
        .withLastUpdated
        .result
        .map(_.map { case (c, messageLastUpdated, noteLastUpdated) =>
          (c, Seq(Option(c.version), messageLastUpdated, noteLastUpdated).flatten.max)
        })
    ).map(Right.apply)

  override def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]] =
    ownerService.getCaseOwners(ids)

  override def setOwners(id: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] =
    ownerService.setCaseOwners(id, owners)

  override def getClients(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[UniversityID]]]] = {
    daoRunner.run(dao.findClientsQuery(ids).result)
      .map(_.groupBy(_.caseId).mapValues(_.map(_.client).toSet))
      .map(Right.apply)
  }

  override def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[UniversityID]]] = {
    getClients(Set(id)).map(_.right.map(_.getOrElse(id, Set.empty)))
  }

  override def addDocument(caseID: UUID, document: CaseDocumentSave, in: ByteSource, file: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseDocument]] =
    auditService.audit('CaseAddDocument, caseID.toString, 'Case, Json.obj()) {
      val documentID = UUID.randomUUID()
      daoRunner.run(for {
        f <- uploadedFileService.storeDBIO(in, file)
        doc <- dao.insertDocument(StoredCaseDocument(
          documentID,
          caseID,
          document.documentType,
          f.id,
          document.teamMember,
          JavaTime.offsetDateTime,
          JavaTime.offsetDateTime
        ))
      } yield doc.asCaseDocument(f)).map(Right.apply)
    }

  private def getDocumentsDBIO(caseID: UUID): DBIO[Seq[(StoredCaseDocument, StoredUploadedFile)]] =
    dao.findDocumentsQuery(caseID)
      .join(UploadedFileDao.uploadedFiles.table).on(_.fileId === _.id)
      .sortBy { case (d, _) => d.created.desc }
      .result

  override def getDocuments(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseDocument]]] = {
    daoRunner.run(getDocumentsDBIO(caseID))
      .map { docs => Right(docs.map { case (d, f) => d.asCaseDocument(f.asUploadedFile) }) }
  }

  override def deleteDocument(caseID: UUID, documentID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit('CaseDocumentDelete, caseID.toString, 'Case, Json.obj("documentID" -> documentID.toString)) {
      daoRunner.run(for {
        existing <- dao.findDocumentsQuery(caseID).filter(_.id === documentID).result.head
        done <- dao.deleteDocument(existing, version)
        _ <- uploadedFileService.deleteDBIO(existing.fileId)
      } yield done).map(Right.apply)
    }

  override def reassign(c: Case, team: Team, caseType: Option[CaseType], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] =
    auditService.audit('CaseReassign, c.id.get.toString, 'Case, Json.obj("team" -> team.id, "caseType" -> caseType.map(_.entryName).orNull[String])) {
      daoRunner.run(
        dao.update(c.copy(team = team, caseType = caseType), version)
      ).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      clientCase => notificationService.caseReassign(clientCase).map(_.right.map(_ => clientCase))
    ))
}

object CaseService {
  def groupTuples(tuples: Seq[(Case, Option[MessageData], Option[StoredCaseNote])]): Seq[(Case, Seq[MessageData], Seq[CaseNote])] = {
    val casesAndMessages =
      OneToMany.leftJoin(tuples.map { case (c, m, _) => (c, m) })(MessageData.dateOrdering)
    val casesAndNotes =
      OneToMany.leftJoin(tuples.map { case (c, _, n) => (c, n.map(_.asCaseNote)) })(CaseNote.dateOrdering).toMap

    sortByRecent(casesAndMessages.map { case (c, m) => (c, m, casesAndNotes.getOrElse(c, Nil)) })
  }

  /**
    * Sort by the most recently updated, either by newest message, newest case note or when the case was last
    * updated (perhaps from its state changing)
    */
  def sortByRecent(data: Seq[(Case, Seq[MessageData], Seq[CaseNote])]): Seq[(Case, Seq[MessageData], Seq[CaseNote])] =
    data.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse)

  def lastModified(entry: (Case, Seq[MessageData], Seq[CaseNote])): OffsetDateTime = {
    import JavaTime.dateTimeOrdering
    val (c, messages, notes) = entry
    (c.version #:: messages.toStream.map(_.created) #::: notes.toStream.map(_.created)).max
  }
}