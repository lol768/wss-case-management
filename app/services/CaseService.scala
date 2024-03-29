package services

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.IssueKeyType.MigratedCase
import domain.Pagination._
import domain.QueryHelpers._
import domain.dao.CaseDao._
import domain.dao.DSADao.{StoredDSAApplication, StoredDSAFundingType}
import domain.dao.MemberDao.StoredMember
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao._
import domain.{Page, _}
import warwick.fileuploads.{UploadedFile, UploadedFileSave}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.slick.helpers.SlickServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.CaseService._
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[CaseServiceImpl])
trait CaseService {
  def create(c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], team: Team, originalEnquiry: Option[UUID], dsaApplication: Option[DSAApplicationSave])(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def importMigrated(c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], team: Team)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]

  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def findAll(id: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def findForView(caseKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def findAllForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseRender]]]
  def listForClient(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[CaseListRender]]]
  def countOpenForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def countClosedForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def findForClient(id: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[CaseRender]]
  def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]
  def search(query: CaseSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]

  def update(caseID: UUID, c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], dsaApplication: Option[DSAApplicationSave], caseVersion: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]
  def updateState(caseID: UUID, targetState: IssueState, version: OffsetDateTime, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]

  def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]]
  def getCaseTags(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[CaseTag]]]
  def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]]

  def addLink(linkType: CaseLinkType, outgoingID: UUID, incomingID: UUID, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[StoredCaseLink]]
  def getLinks(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[(Seq[CaseLink], Seq[CaseLink])]]
  def deleteLink(caseID: UUID, linkID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]

  def addGeneralNote(caseID: UUID, note: CaseNoteSave, ownersOnly: Boolean)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]]
  def addNoteDBIO(caseID: UUID, noteType: CaseNoteType, note: CaseNoteSave)(implicit ac: AuditLogContext): DBIO[StoredCaseNote]
  def getNote(id: UUID)(implicit t: TimingContext): Future[ServiceResult[NoteAndCase]]
  def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNoteRender]]]
  def updateNote(caseID: UUID, noteID: UUID, note: CaseNoteSave, noteType: CaseNoteType, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]]
  def deleteNote(caseID: UUID, noteID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]

  def listCases(filter: CaseFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[CaseListRender]]]
  def countCases(filter: CaseFilter)(implicit t: TimingContext): Future[ServiceResult[Int]]
  def getOwnersMatching(filter: CaseFilter)(implicit t: TimingContext): Future[ServiceResult[Seq[Member]]]

  def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]
  def setOwners(id: UUID, owners: Set[Usercode], note: Option[CaseNoteSave])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]]

  def getClients(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Client]]]]
  def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[Client]]]

  def addDocument(caseID: UUID, document: CaseDocumentSave, in: ByteSource, file: UploadedFileSave, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseDocument]]
  def getDocuments(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseDocument]]]
  def deleteDocument(caseID: UUID, documentID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]

  def addMessage(`case`: Case, client: UniversityID, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]]
  def hasMessagesForClient(id: UUID, client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def getCaseMessages(id: UUID)(implicit t: TimingContext): Future[ServiceResult[CaseMessages]]

  def reassign(c: Case, team: Team, caseType: Option[CaseType], note: CaseNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]]

  def getHistory(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[CaseHistory]]

  def findFromOriginalEnquiry(enquiryId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]]

  def getLastUpdatedForClients(clients: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Option[OffsetDateTime]]]]

  def findDSAApplication(`case`: Case)(implicit t: TimingContext): Future[ServiceResult[Option[DSAApplication]]]

  def getLastUpdatedMessageDates(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, OffsetDateTime]]]
  def getLastUpdatedMessageDates(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Map[UniversityID, OffsetDateTime]]]]

}

@Singleton
class CaseServiceImpl @Inject() (
  auditService: AuditService,
  ownerService: OwnerService,
  uploadedFileService: UploadedFileService,
  notificationService: NotificationService,
  appointmentService: AppointmentService,
  userLookupService: UserLookupService,
  permissionsService: PermissionService,
  clientService: ClientService,
  memberService: MemberService,
  daoRunner: DaoRunner,
  dao: CaseDao,
  dsaDao: DSADao,
  messageDao: MessageDao,
)(implicit ec: ExecutionContext) extends CaseService {

  private def createStoredCase(id: UUID, key: IssueKey, save: CaseSave, team: Team, originalEnquiry: Option[UUID], dsaApplication: Option[UUID]): StoredCase =
    StoredCase(
      id = id,
      key = key,
      subject = save.subject,
      created = JavaTime.offsetDateTime,
      team: Team,
      version = JavaTime.offsetDateTime,
      state = IssueState.Open,
      incidentDate = save.incident.map(_.incidentDate),
      onCampus = save.incident.map(_.onCampus),
      notifiedPolice = save.incident.map(_.notifiedPolice),
      notifiedAmbulance = save.incident.map(_.notifiedAmbulance),
      notifiedFire = save.incident.map(_.notifiedFire),
      originalEnquiry = originalEnquiry,
      caseType = save.caseType,
      cause = save.cause,
      dsaApplication = dsaApplication,
      fields = StoredCaseFields(
        clientRiskTypes = save.clientRiskTypes.map(_.entryName).toList.sorted,
        counsellingServicesIssues = save.counsellingServicesIssues.map(_.entryName).toList.sorted,
        studentSupportIssueTypes = save.studentSupportIssueTypes.map(_.entryName).toList.sorted,
        studentSupportIssueTypeOther = StudentSupportIssueType.otherValue(save.studentSupportIssueTypes),
        mentalHealthIssues = save.mentalHealthIssues.map(_.entryName).toList.sorted,
        medications = save.medications.map(_.entryName).toList.sorted,
        medicationOther = CaseMedication.otherValue(save.medications),
        severityOfProblem = save.severityOfProblem,
        duty = save.duty,
      )
    )

  private def insertCase(id: UUID, caseType: IssueKeyType, c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], team: Team, originalEnquiry: Option[UUID], application: Option[DSAApplicationSave])(implicit ac: AuditLogContext): DBIO[StoredCase] = {
    assert(caseType == IssueKeyType.Case || caseType == IssueKeyType.MigratedCase)

    val now = JavaTime.offsetDateTime

    val dsaInsert: DBIO[Option[StoredDSAApplication]] =
      application.map(a => dsaDao.insert(a.asStoredApplication(UUID.randomUUID(), now)).map(Some.apply)).getOrElse(DBIO.successful(None))

    def fundingTypesInsert(newApplication: Option[UUID]): DBIO[Seq[StoredDSAFundingType]] = (for {
      a <- application
      na <- newApplication
    } yield dsaDao.insertFundingTypes(a.fundingTypes.map { ft => StoredDSAFundingType(na, ft, now) })).getOrElse(DBIO.successful(Nil))

    for {
      nextId <- sql"SELECT nextval('SEQ_CASE_ID')".as[Int].head
      dsa <- dsaInsert
      _ <- fundingTypesInsert(dsa.map(_.id))
      inserted <- dao.insert(createStoredCase(id, IssueKey(caseType, nextId), c, team, originalEnquiry, dsa.map(_.id)))
      _ <- dao.insertClients(clients.map { universityId => StoredCaseClient(id, universityId, now) })
      _ <- dao.insertTags(tags.map { t => StoredCaseTag(id, t, now) })
    } yield inserted
  }

  override def create(c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], team: Team, originalEnquiry: Option[UUID], application: Option[DSAApplicationSave])(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    val id = UUID.randomUUID()

    auditService.audit(Operation.Case.Save, id.toString, Target.Case, Json.obj()) {
      clientService.getOrAddClients(clients).successFlatMapTo { _ =>
        daoRunner.run(insertCase(id, IssueKeyType.Case, c, clients, tags, team, originalEnquiry, application))
          .map { sc => Right(sc.asCase) }
      }
    }
  }

  private[this] lazy val migratedCaseEpoch: OffsetDateTime =
    OffsetDateTime.of(2018, 12, 31, 12, 0, 0, 0, ZoneOffset.UTC)

  override def importMigrated(c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], team: Team)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    val id = UUID.randomUUID()

    auditService.audit(Operation.Case.ImportMigrated, id.toString, Target.Case, Json.obj()) {
      clientService.getOrAddClients(clients).successFlatMapTo { _ =>
        daoRunner.run(for {
          inserted <- insertCase(id, IssueKeyType.MigratedCase, c, clients, tags, team, None, None)
          updated <- dao.update(inserted.copy(
            state = IssueState.Closed,

            // Transition back in time so these don't show up in stats
            created = migratedCaseEpoch,
          ), inserted.version)
          _ <- addNoteDBIO(id, CaseNoteType.CaseClosed, CaseNoteSave("Case closed on migration", Usercode("system"), None))
        } yield updated).map { sc => Right(sc.asCase) }
      }
    }
  }

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(id)).map { sc => ServiceResults.success(sc.asCase) }.recover {
      case _: NoSuchElementException => ServiceResults.error[Case](s"Could not find a Case with ID $id")
    }

  override def find(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    daoRunner.run(dao.find(ids.toSet)).map { cases =>
      val lookup = cases.groupBy(_.id).mapValues(_.head.asCase)

      if (ids.forall(lookup.contains))
        Right(ids.map(lookup.apply))
      else
        Left(ids.filterNot(lookup.contains).toList.map { id => ServiceError(s"Could not find a Case with ID $id") })
    }

  override def find(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(caseKey)).map { sc => ServiceResults.success(sc.asCase) }.recover {
      case _: NoSuchElementException => ServiceResults.error[Case](s"Could not find a Case with key ${caseKey.string}")
    }

  override def findAll(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    if (ids.isEmpty) Future.successful(Right(Nil))
    else daoRunner.run(dao.find(ids)).map { sc => Right(sc.map(_.asCase)) }

  override def findForView(caseKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] =
    auditService.audit(Operation.Case.View, (c: Case) => c.id.toString, Target.Case, Json.obj()) {
      find(caseKey)
    }

  override def findAllForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseRender]]] = {
    withClientMessagesAndNotes(universityID, dao.findByClientQuery(universityID)).map(Right.apply)
  }

  override def listForClient(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[CaseListRender]]] = {
    daoRunner.run(
      dao.findByClientQuery(universityID)
        .withLastUpdatedFor(ac.usercode.orNull)
        .sortBy { case (c, lu, _, _) => (lu.desc, c.key.desc) }
        .result
    ).map { results => Right(results.map { case (c, lastUpdated, lastMessageFromClient, lastViewed) =>
      CaseListRender(c.asCase, lastUpdated, lastMessageFromClient, lastViewed) })
    }
  }

  override def countOpenForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]] = {
    daoRunner.run(
      dao.findByClientQuery(universityID)
        .filter(_.isOpen)
        .size
        .result
    ).map(Right.apply)
  }

  override def countClosedForClient(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Int]] = {
    daoRunner.run(
      dao.findByClientQuery(universityID)
        .filter(!_.isOpen)
        .size
        .result
    ).map(Right.apply)
  }

  override def findForClient(id: UUID, universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[CaseRender]] = {
    auditService.audit(Operation.Case.View, id.toString, Target.Case, Json.obj()) {
      withClientMessagesAndNotes(universityID, dao.findByIDQuery(id)).map(r => Right(r.head))
    }
  }

  private def withClientMessagesAndNotes(universityID: UniversityID, query: Query[Cases, StoredCase, Seq])(implicit t: TimingContext): Future[Seq[CaseRender]] = {
    daoRunner.run(for {
      withMessages <- query.withMessages
        .map { case (c, mf) => (c,
          mf.filter { case (m, _, _) => m.client === universityID }
        )
        }
        .result
      notes <- dao.findNotesQuery(withMessages.map { case (c, _) => c.id }.toSet).withMember.result
    } yield (withMessages, notes)).map { case (withMessages, notes) =>
      groupTuples(withMessages, notes)
    }
  }

  override def findRecentlyViewed(teamMember: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    auditService.findRecentTargetIDsByOperation(Operation.Case.View, teamMember, limit).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      ids => find(ids.map(UUID.fromString))
    ))

  override def search(query: CaseSearchQuery, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] =
    daoRunner.run(dao.searchQuery(query).take(limit).result).map { sc => Right(sc.map(_.asCase)) }

  override def update(caseID: UUID, c: CaseSave, clients: Set[UniversityID], tags: Set[CaseTag], application: Option[DSAApplicationSave], caseVersion: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    auditService.audit(Operation.Case.Update, caseID.toString, Target.Case, Json.obj()) {
      clientService.getOrAddClients(clients).successFlatMapTo { _ =>
        val now = JavaTime.offsetDateTime

        daoRunner.run(for {
          existing <- dao.find(caseID)
          existingDSA <- existing.dsaApplication match {
            case Some(dsaID) => findDSAApplicationDBIO(dsaID).map(Some.apply)
            case _ => DBIO.successful(None)
          }
          dsa <- (application, existingDSA) match {
            case (Some(a), Some(e)) => dsaDao.update(a.asStoredApplication(existing.dsaApplication.get, e.lastUpdated)).map(Some.apply)
            case (Some(a), None) => dsaDao.insert(a.asStoredApplication(UUID.randomUUID(), now)).map(Some.apply) // create a new DSA application
            case (None, Some(e)) => dsaDao.delete(e.asStoredApplication(existing.dsaApplication.get)).map(_ => None) // delete the existing DSA application
            case _ => DBIO.successful(None)
          }
          updated <- dao.update(
            // We re-construct the whole StoredCase here so that missing a value will throw a compile error
            StoredCase(
              id = existing.id,
              key = existing.key,
              subject = c.subject,
              created = existing.created,
              team = existing.team,
              version = JavaTime.offsetDateTime,
              state = existing.state,
              incidentDate = c.incident.map(_.incidentDate),
              onCampus = c.incident.map(_.onCampus),
              notifiedPolice = c.incident.map(_.notifiedPolice),
              notifiedAmbulance = c.incident.map(_.notifiedAmbulance),
              notifiedFire = c.incident.map(_.notifiedFire),
              originalEnquiry = existing.originalEnquiry,
              caseType = c.caseType,
              cause = c.cause,
              dsaApplication = dsa.map(_.id),
              fields = StoredCaseFields(
                clientRiskTypes = c.clientRiskTypes.map(_.entryName).toList.sorted,
                counsellingServicesIssues = c.counsellingServicesIssues.map(_.entryName).toList.sorted,
                studentSupportIssueTypes = c.studentSupportIssueTypes.map(_.entryName).toList.sorted,
                studentSupportIssueTypeOther = StudentSupportIssueType.otherValue(c.studentSupportIssueTypes),
                mentalHealthIssues = c.mentalHealthIssues.map(_.entryName).toList.sorted,
                medications = c.medications.map(_.entryName).toList.sorted,
                medicationOther = CaseMedication.otherValue(c.medications),
                severityOfProblem = c.severityOfProblem,
                duty = c.duty,
              )
            ),
            caseVersion
          )
          _ <- updateDifferencesDBIO[StoredCaseClient, UniversityID](
            clients,
            dao.findClientsQuery(Set(caseID)).map { case (client, _) => client },
            _.universityID,
            id => StoredCaseClient(caseID, id, now),
            dao.insertClients,
            dao.deleteClients
          )
          _ <- updateDifferencesDBIO[StoredDSAFundingType, DSAFundingType](
            application.map(_.fundingTypes).getOrElse(Set()),
            dsaDao.findFundingTypesQuery(existing.dsaApplication.orElse(dsa.map(_.id)).toSet),
            _.fundingType,
            ft => StoredDSAFundingType(dsa.get.id, ft, now),
            dsaDao.insertFundingTypes,
            dsaDao.deleteFundingTypes
          )
          _ <- updateDifferencesDBIO[StoredCaseTag, CaseTag](
            tags,
            dao.findTagsQuery(Set(caseID)),
            _.caseTag,
            t => StoredCaseTag(caseID, t, now),
            dao.insertTags,
            dao.deleteTags
          )
        } yield updated).map { sc => Right(sc.asCase) }
      }
    }
  }

  override def updateState(caseID: UUID, targetState: IssueState, version: OffsetDateTime, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] = {
    val noteType = targetState match {
      case IssueState.Closed => CaseNoteType.CaseClosed
      case IssueState.Reopened => CaseNoteType.CaseReopened
      case _ => throw new IllegalArgumentException(s"Invalid target state $targetState")
    }

    def checkValidTransition(clientCase: StoredCase): Future[Unit] = Future.successful {
      if (clientCase.key.keyType == MigratedCase) {
        // Mostly to stop reopening but they shouldn't change at all.
        throw new IllegalStateException("Migrated cases cannot be transitioned")
      }
    }

    auditService.audit(Operation.Case.transition(targetState), caseID.toString, Target.Case, Json.obj()) {
      memberService.getOrAddMember(caseNote.teamMember).successFlatMapTo(_ =>
        daoRunner.run(for {
          clientCase <- dao.find(caseID)
          _ <- DBIO.from(checkValidTransition(clientCase))
          updated <- dao.update(clientCase.copy(state = targetState), version)
          _ <- addNoteDBIO(caseID, noteType, caseNote)
        } yield updated).map { sc => Right(sc.asCase) }
      )
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
    auditService.audit(Operation.Case.SetTags, caseId.toString, Target.Case, Json.toJson(tags)) {
      val now = JavaTime.offsetDateTime
      daoRunner.run(updateDifferencesDBIO[StoredCaseTag, CaseTag](
        tags,
        dao.findTagsQuery(Set(caseId)),
        _.caseTag,
        t => StoredCaseTag(caseId, t, now),
        dao.insertTags,
        dao.deleteTags
      )).map(_ => Right(tags))
    }

  override def addLink(linkType: CaseLinkType, outgoingID: UUID, incomingID: UUID, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[StoredCaseLink]] =
    auditService.audit(Operation.Case.AddLink, outgoingID.toString, Target.Case, Json.obj("to" -> incomingID.toString, "note" -> caseNote.text)) {
      memberService.getOrAddMember(caseNote.teamMember).successFlatMapTo(_ =>
        daoRunner.run(for {
          outNote <- addNoteDBIO(outgoingID, CaseNoteType.AssociatedCase, caseNote)
          // add a note to the linked case - will bump last modified on that case. May want to show it in the UI at some point but won't be exposed for now
          _ <- addNoteDBIO(incomingID, CaseNoteType.AssociatedCase, caseNote)
          link <- dao.insertLink(StoredCaseLink(UUID.randomUUID(), linkType, outgoingID, incomingID, outNote.id, caseNote.teamMember))
        } yield link).map(Right.apply)
      )
    }

  private def getLinksDBIO(caseID: UUID): DBIO[(Seq[CaseLink], Seq[CaseLink])] =
    dao.findLinksQuery(caseID).withMember
      .join(CaseDao.cases.table).on { case ((l, _), c) => l.outgoingCaseID === c.id }
      .flattenJoin
      .join(CaseDao.cases.table).on { case ((l, _, _), i) => l.incomingCaseID === i.id }
      .flattenJoin
      .join(CaseDao.caseNotes.table.withMember).on { case ((l, _, _, _), (n, _)) => l.caseNote === n.id }
      .map { case ((l, lm, o, i), (n, nm)) => (l, lm, o, i, n, nm) }
      .result
      .map { results =>
        results.map { case (link, linkMember, outgoing, incoming, note, noteMember) =>
          CaseLink(link.id, link.linkType, outgoing.asCase, incoming.asCase, note.asCaseNote(noteMember.asMember), linkMember.asMember, link.version)
        }.partition(_.outgoing.id == caseID)
      }

  override def getLinks(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[(Seq[CaseLink], Seq[CaseLink])]] =
    daoRunner.run(getLinksDBIO(caseID)).map(Right.apply)

  override def deleteLink(caseID: UUID, linkID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Case.DeleteLink, caseID.toString, Target.Case, Json.obj("linkID" -> linkID.toString)) {
      daoRunner.run(for {
        existing <- dao.findLinksQuery(caseID).filter(_.id === linkID).result.head
        done <- dao.deleteLink(existing, version)
      } yield done).map(Right.apply)
    }

  override def addNoteDBIO(caseID: UUID, noteType: CaseNoteType, note: CaseNoteSave)(implicit ac: AuditLogContext): DBIO[StoredCaseNote] =
    dao.insertNote(
      StoredCaseNote(
        id = UUID.randomUUID(),
        caseId = caseID,
        noteType = noteType,
        text = note.text,
        teamMember = note.teamMember,
        appointmentId = note.appointmentID,
        created = JavaTime.offsetDateTime,
        version = JavaTime.offsetDateTime
      )
    )

  override def addGeneralNote(caseID: UUID, note: CaseNoteSave, ownersOnly: Boolean)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]] =
    auditService.audit(Operation.Case.AddGeneralNote, caseID.toString, Target.Case, Json.obj("ownersOnly" -> ownersOnly)) {
      memberService.getOrAddMember(note.teamMember).successFlatMapTo(member =>
        daoRunner.run(addNoteDBIO(caseID, if (ownersOnly) CaseNoteType.SensitiveGeneralNote else CaseNoteType.GeneralNote, note))
          .map { n => Right(n.asCaseNote(member)) }
      )
    }

  override def getNote(id: UUID)(implicit t: TimingContext): Future[ServiceResult[NoteAndCase]] =
    daoRunner.run(dao.findNote(id)).map(Right.apply)

  private def getNotesDBIO(caseID: UUID): DBIO[Seq[(StoredCaseNote, StoredMember)]] =
    dao.findNotesQuery(caseID).withMember.sortBy { case (notes, _) => notes.created.desc }.result

  override def getNotes(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseNoteRender]]] =
    daoRunner.run(getNotesDBIO(caseID)).flatMap { notes =>
      val appointmentIDs = notes.flatMap(_._1.appointmentId).distinct

      val appointments: Future[ServiceResult[Map[UUID, AppointmentRender]]] =
        if (appointmentIDs.isEmpty) Future.successful(ServiceResults.success(Map()))
        else appointmentService.findFull(appointmentIDs).successMapTo(_.map { a => a.appointment.id -> a }.toMap)

      appointments.successMapTo { appointmentLookup =>
        notes.map { case (n, m) =>
          CaseNoteRender(n.asCaseNote(m.asMember), n.appointmentId.map(appointmentLookup.apply))
        }
      }
    }

  override def updateNote(caseID: UUID, noteID: UUID, note: CaseNoteSave, noteType: CaseNoteType, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[CaseNote]] =
    auditService.audit(Operation.Case.UpdateNote, caseID.toString, Target.Case, Json.obj("noteID" -> noteID.toString)) {
      memberService.getOrAddMember(note.teamMember).successFlatMapTo(member =>
        daoRunner.run(for {
          existing <- dao.findNotesQuery(caseID).filter(_.id === noteID).result.head
          updated <- dao.updateNote(existing.copy(text = note.text, noteType = noteType, teamMember = note.teamMember), version)
        } yield updated).map { n => Right(n.asCaseNote(member)) }
      )
    }

  override def deleteNote(caseID: UUID, noteID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Case.DeleteNote, caseID.toString, Target.Case, Json.obj("noteID" -> noteID.toString)) {
      daoRunner.run(for {
        existing <- dao.findNotesQuery(caseID).filter(_.id === noteID).result.head
        done <- dao.deleteNote(existing, version)
      } yield done).map(Right.apply)
    }

  override def listCases(filter: CaseFilter, listFilter: IssueListFilter, page: Page)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[CaseListRender]]] =
    daoRunner.run(
      dao.listQuery(filter)
        .withLastUpdatedFor(ac.usercode.orNull)
        .filter { case (_, lastUpdated, lastMessageFromClient, lastViewed) =>
          val lastUpdatedAfterFilter = listFilter.lastUpdatedAfter.fold(true.bind)(lastUpdated > _)
          val lastUpdatedBeforeFilter = listFilter.lastUpdatedBefore.fold(true.bind)(lastUpdated < _)
          val hasUnreadsFilter = listFilter.hasUnreadClientMessages.fold(true.bind.?) {
            case true => lastMessageFromClient.nonEmpty.? && (lastViewed.isEmpty.? || lastViewed < lastMessageFromClient)
            case false => lastMessageFromClient.isEmpty.? || (lastViewed.nonEmpty.? && lastViewed >= lastMessageFromClient)
          }

          lastUpdatedAfterFilter && lastUpdatedBeforeFilter && hasUnreadsFilter
        }
        .sortBy { case (c, lu, _, _) => (lu.desc, c.key.desc) }
        .paginate(page)
        .result
    ).map { results => Right(results.map { case (c, lastUpdated, lastMessageFromClient, lastViewed) =>
      CaseListRender(c.asCase, lastUpdated, lastMessageFromClient, lastViewed) })
    }

  override def countCases(filter: CaseFilter)(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(
      dao.listQuery(filter).length.result
    ).map(Right.apply)

  override def getOwnersMatching(filter: CaseFilter)(implicit t: TimingContext): Future[ServiceResult[Seq[Member]]] =
    daoRunner.run(
      dao.listQuery(filter)
        .join(Owner.owners.table)
        .on { case (c, o) => c.id === o.entityId && o.entityType === (Owner.EntityType.Case: Owner.EntityType) }
        .join(MemberDao.members.table)
        .on { case ((_, o), m) => o.userId === m.usercode }
        .map { case (_, m) => m }
        .distinct
        .result
    ).map { results => Right(results.map(_.asMember).sorted) }

  override def getOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]] =
    ownerService.getCaseOwners(ids)

  override def setOwners(id: UUID, owners: Set[Usercode], note: Option[CaseNoteSave])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]] = {
    ServiceResults.zip(
      ownerService.setCaseOwners(id, owners),
      daoRunner.run(
        note.map(n => addNoteDBIO(id, CaseNoteType.OwnerNote, n)).getOrElse(DBIO.successful(()))
      ).map(Right.apply)
    ).successMapTo { case (ownerChanges, _)  => ownerChanges }
  }

  override def getClients(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Client]]]] = {
    daoRunner.run(dao.findClientsQuery(ids).result)
      .map(_.groupBy { case (c, _) => c.caseId }.mapValues(_.map { case (_, c) => c.asClient }.toSet))
      .map(Right.apply)
  }

  override def getClients(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Set[Client]]] = {
    getClients(Set(id)).map(_.right.map(_.getOrElse(id, Set.empty)))
  }

  override def addDocument(caseID: UUID, document: CaseDocumentSave, in: ByteSource, file: UploadedFileSave, caseNote: CaseNoteSave)(implicit ac: AuditLogContext): Future[ServiceResult[CaseDocument]] =
    auditService.audit(Operation.Case.AddDocument, caseID.toString, Target.Case, Json.obj()) {
      memberService.getOrAddMembers(Set(document.teamMember, caseNote.teamMember)).successFlatMapTo { members =>
        val documentID = UUID.randomUUID()
        daoRunner.run(for {
          f <- uploadedFileService.storeDBIO(in, file, ac.usercode.get)
          n <- addNoteDBIO(caseID, CaseNoteType.DocumentNote, caseNote)
          doc <- dao.insertDocument(StoredCaseDocument(
            documentID,
            caseID,
            document.documentType,
            f.id,
            document.teamMember,
            n.id,
            JavaTime.offsetDateTime,
            JavaTime.offsetDateTime
          ))

        } yield doc.asCaseDocument(
          f,
          n.asCaseNote(members.find(_.usercode == n.teamMember).get),
          members.find(_.usercode == doc.teamMember).get
        )).map(Right.apply)
      }
    }

  private def getDocumentsDBIO(caseID: UUID): DBIO[Seq[(StoredCaseDocument, StoredUploadedFile, StoredCaseNote, StoredMember, StoredMember)]] =
    dao.findDocumentsQuery(caseID)
      .join(UploadedFileDao.uploadedFiles.table).on(_.fileId === _.id)
      .join(CaseDao.caseNotes.table).on(_._1.caseNote === _.id)
      .join(MemberDao.members.table).on { case (((d, _), _), m) => d.teamMember === m.usercode }
      .join(MemberDao.members.table).on { case ((((_, _), n), _), m) => n.teamMember === m.usercode }
      .flattenJoin
      .sortBy { case (d, _, _, _, _) => d.created.desc }
      .result

  override def getDocuments(caseID: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[CaseDocument]]] = {
    daoRunner.run(getDocumentsDBIO(caseID))
      .map { docs => Right(docs.map { case (d, f, n, docMember, noteMember) => d.asCaseDocument(f.asUploadedFile, n.asCaseNote(noteMember.asMember), docMember.asMember) }) }
  }

  override def deleteDocument(caseID: UUID, documentID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Case.DeleteDocument, caseID.toString, Target.Case, Json.obj("documentID" -> documentID.toString)) {
      daoRunner.run(for {
        existing <- dao.findDocumentsQuery(caseID).filter(_.id === documentID).result.head
        done <- dao.deleteDocument(existing, version)
        _ <- uploadedFileService.deleteDBIO(existing.fileId)
      } yield done).map(Right.apply)
    }


  override def addMessage(`case`: Case, client: UniversityID, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[(MessageData, Seq[UploadedFile])]] = {
    auditService.audit(Operation.Case.AddMessage, `case`.id.toString, Target.Case, Json.obj("client" -> client.string)) {
      memberService.getOrAddMember(message.teamMember).successFlatMapTo(member =>
        daoRunner.runWithServiceResult(for {
          (m, file) <- addMessageDBIO(`case`, client, message, files, ac.usercode.get)
          ownerMap <- getOwners(Set(`case`.id)).toDBIO
          _ <- notificationService.caseMessage(`case`, ownerMap.right.get.getOrElse(`case`.id, Set.empty).map(_.usercode), client, m.sender).toDBIO
        } yield (m.asMessageData(member), file))
      )
    }
  }

  override def hasMessagesForClient(id: UUID, client: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    withClientMessagesAndNotes(client, dao.findByIDQuery(id)).map(r => Right(r.head.messages.nonEmpty))

  override def getCaseMessages(id: UUID)(implicit t: TimingContext): Future[ServiceResult[CaseMessages]] = {
    daoRunner.run(for {
      (_, messages) <- dao.findByIDQuery(id).withMessages
        .sortBy { case (_, mf) => mf.map(_._1.created) }
        .result
        .map { results =>
          MessageData.groupOwnerAndMessage[Case](results.map { case (c, m) => (
            c.asCase,
            m.map { case (msg, f, member) => (msg.asMessageData(member.map(_.asMember)), f) }
          )
          })
        }
        .map {
          _.head
        }
    } yield {
      Right(CaseMessages(messages))
    })
  }

  private def addMessageDBIO(`case`: Case, client: UniversityID, message: MessageSave, files: Seq[(ByteSource, UploadedFileSave)], uploader: Usercode)(implicit ac: AuditLogContext): DBIO[(Message, Seq[UploadedFile])] =
    for {
      message <- messageDao.insert(message.toMessage(
        client = client,
        team = `case`.team,
        ownerId = `case`.id,
        ownerType = MessageOwner.Case
      ))
      f <- DBIO.sequence(files.map { case (in, metadata) =>
        uploadedFileService.storeDBIO(in, metadata, uploader, message.id, UploadedFileOwner.Message)
      })
    } yield (message, f)

  override def reassign(c: Case, team: Team, caseType: Option[CaseType], note: CaseNoteSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Case]] =
    auditService.audit(Operation.Case.Reassign, c.id.toString, Target.Case, Json.obj("team" -> team.id, "caseType" -> caseType.map(_.entryName).orNull[String])) {
      daoRunner.runWithServiceResult(for {
        existing <- dao.find(c.id)
        sc <- dao.update(existing.copy(team = team, caseType = caseType), version)
        _ <- addNoteDBIO(c.id, CaseNoteType.Referral, note)
        _ <- notificationService.caseReassign(sc.asCase).toDBIO
      } yield sc.asCase)
    }

  override def getHistory(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[CaseHistory]] = {
    ownerService.getCaseOwnerHistory(id).flatMap(result => result.fold(
      errors => Future.successful(Left.apply(errors)),
      rawOwnerHistory => {
        daoRunner.run(for {
          caseHistory <- dao.getHistory(id)
          rawTagHistory <- dao.getTagHistory(id)
          rawClientHistory <- dao.getClientHistory(id)
          rawDsaHistory <- dsaDao.getDSAHistory(id)
          rawDSAFundingTypeHistory <- dsaDao.getDSAFundingTypeHistory(id)
        } yield {
          (caseHistory, rawTagHistory, rawClientHistory, rawDsaHistory, rawDSAFundingTypeHistory)
        }).flatMap { case (caseHistory, rawTagHistory, rawClientHistory, rawDsaHistory, rawDSAFundingTypeHistory) =>
          CaseHistory.apply(caseHistory, rawTagHistory, rawOwnerHistory, rawClientHistory, rawDsaHistory, rawDSAFundingTypeHistory, userLookupService, clientService)
        }
      }
    ))
  }

  override def findFromOriginalEnquiry(enquiryId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Case]]] = {
    daoRunner.run(dao.findByOriginalEnquiryQuery(enquiryId).result).map { sc => Right(sc.map(_.asCase)) }
  }

  override def getLastUpdatedForClients(clients: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Option[OffsetDateTime]]]] =
    daoRunner.run(dao.getLastUpdatedForClients(clients)).map(r => Right(r.toMap.withDefaultValue(None)))

  override def findDSAApplication(`case`: Case)(implicit t: TimingContext): Future[ServiceResult[Option[DSAApplication]]] = {
    `case`.dsaApplication.map(dsaId =>
      daoRunner.run(findDSAApplicationDBIO(dsaId).map(Some.apply))
    ).getOrElse(Future.successful(None)).map(Right(_)).recover[ServiceResult[Option[DSAApplication]]] {
      case _: NoSuchElementException => ServiceResults.error[Option[DSAApplication]](s"Could not find a DSA application with ID ${`case`.dsaApplication.get}")
    }
  }

  private def findDSAApplicationDBIO(dsaID: UUID): DBIO[DSAApplication] =
    for {
      application <- dsaDao.findDSAApplication(dsaID)
      fundingTypes <- dsaDao.findFundingTypesQuery(Set(dsaID)).result
    } yield DSAApplication(application, fundingTypes.map(_.fundingType).toSet)

  override def getLastUpdatedMessageDates(caseKey: IssueKey)(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, OffsetDateTime]]] =
    daoRunner.run(
      dao.findByKeyQuery(caseKey)
        .join(Message.lastUpdatedCasePerClientMessage)
        .on { case (c, (id, _, _)) => c.id === id }
        .result
    ).map(r => Right(r.map { case (_, (_, c, d)) => (c, d.get) }.toMap))

  override def getLastUpdatedMessageDates(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Map[UniversityID, OffsetDateTime]]]] =
    daoRunner.run(
      dao.findByIDsQuery(ids)
        .join(Message.lastUpdatedCasePerClientMessage)
        .on { case (c, (id, _, _)) => c.id === id }
        .result
    ).map(r => Right(
      r.map { case (clientCase, (_, c, d)) => (clientCase.id, c, d.get) }
        .groupBy(_._1).mapValues(_.groupBy(_._2).mapValues(_.head._3))
    ))
}

object CaseService {
  def groupTuples(messagesTuples: Seq[(StoredCase, Option[(Message, Option[StoredUploadedFile], Option[StoredMember])])], notes: Seq[(StoredCaseNote, StoredMember)]): Seq[CaseRender] = {
    val casesAndMessages = MessageData.groupOwnerAndMessage(
      messagesTuples.map { case (c, m) => (
        c,
        m.map { case (msg, f, member) => (msg.asMessageData(member.map(_.asMember)), f) }
      ) }
    )

    val notesByCase = notes.groupBy { case (n, _) => n.caseId }
      .mapValues(_.map {
        case (n, m) => n.asCaseNote(m.asMember)
      }.sorted(CaseNote.dateOrdering)).withDefaultValue(Seq())

    sortByRecent(casesAndMessages.map { case (c, m) => CaseRender(c.asCase, m.distinct, notesByCase(c.id)) })
  }

  /**
    * Sort by the most recently updated, either by newest message, newest case note or when the case was last
    * updated (perhaps from its state changing)
    */
  def sortByRecent(data: Seq[CaseRender]): Seq[CaseRender] =
    data.sortBy(lastModified)(JavaTime.dateTimeOrdering.reverse)

  def lastModified(entry: CaseRender): OffsetDateTime = {
    import JavaTime.dateTimeOrdering
    (entry.clientCase.lastUpdated #:: entry.messages.toStream.map(_.message.created) #::: entry.notes.toStream.map(_.created)).max
  }
}
