package services

import java.io.InputStream
import java.nio.charset.StandardCharsets

import akka.Done
import com.google.common.io.ByteSource
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.CaseDao.{CaseSearchQuery, StoredCaseClient}
import domain.dao.ClientDao.StoredClient
import domain.dao.{AbstractDaoTest, CaseDao, ClientDao, UploadedFileDao}
import helpers.DataFixture
import warwick.core.timing.TimingContext
import warwick.objectstore.ObjectStorageService
import warwick.sso.{UniversityID, Usercode}

class CaseServiceTest extends AbstractDaoTest {

  private val service = get[CaseService]
  private val objectStorageService = get[ObjectStorageService]

  class CaseFixture extends DataFixture[Case] {
    override def setup(): Case = {
      // Ensure that the case has a client, it's required and searching relies on it being a join not a left join
      val c = execWithCommit(
        CaseDao.cases.insert(Fixtures.cases.newStoredCase())
      ).asCase

      execWithCommit(
        ClientDao.clients.insert(StoredClient(UniversityID("3344556"), Some("Jonathan Testman"))) andThen
        CaseDao.caseClients.insert(StoredCaseClient(c.id, UniversityID("3344556")))
      )

      c
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "CaseServiceTest" should {
    "create" in withData(new CaseFixture()) { reference =>
      val created = service.create(CaseSave(reference), Set(UniversityID("0672089")), Set.empty, Teams.MentalHealth, None, None).serviceValue
      created.key.string mustBe "CAS-1000"

      val dsaApplication = Fixtures.cases.newDSAApplicationSave()
      val created2 = service.create(CaseSave(reference), Set(UniversityID("0672089"), UniversityID("0672088")), Set(CaseTag.Drugs, CaseTag.HomeSickness), Teams.MentalHealth, None, Some(dsaApplication)).serviceValue
      created2.key.string mustBe "CAS-1001"
      created2.dsaApplication.isDefined mustBe true
      service.findDSAApplication(created2).serviceValue.isDefined mustBe true
    }

    "find" in withData(new CaseFixture()) { case1 =>
      // Can find by either UUID or IssueKey
      service.find(case1.id).serviceValue.id mustBe case1.id
      service.find(case1.key).serviceValue.id mustBe case1.id
    }

    "find by client" in withData(new CaseFixture()) { reference =>
      val definedAuditLogContext: AuditLogContext = AuditLogContext(Some(Usercode("cusfal")), timingData = TimingContext.none.timingData)

      val created = service.create(CaseSave(reference), Set(UniversityID("0672089")), Set.empty, Teams.MentalHealth, None, None)(definedAuditLogContext).serviceValue
      val created2 = service.create(CaseSave(reference), Set(UniversityID("0672089"), UniversityID("0672088")), Set(CaseTag.Drugs, CaseTag.HomeSickness), Teams.MentalHealth, None, None)(definedAuditLogContext).serviceValue

      service.findAllForClient(UniversityID("0672089")).serviceValue.map(_.clientCase) mustBe Seq(created2, created)
      service.findAllForClient(UniversityID("0672088")).serviceValue.map(_.clientCase) mustBe Seq(created2)
      service.findAllForClient(UniversityID("1234567")).serviceValue mustBe 'empty

      // Only return messages for specified client (but still return the case)
      service.addMessage(created2, UniversityID("0672088"), MessageSave("text", MessageSender.Team, Some(Usercode("cusfal"))), Seq())(definedAuditLogContext)
      val resultFor0672088 = service.findAllForClient(UniversityID("0672088")).serviceValue
      resultFor0672088.size mustBe 1
      resultFor0672088.head.clientCase mustBe created2
      resultFor0672088.head.messages.size mustBe 1
      val resultFor0672089 = service.findAllForClient(UniversityID("0672089")).serviceValue
      resultFor0672089.size mustBe 2
      resultFor0672089.find(_.clientCase == created2).get.messages.size mustBe 0
    }

    "get and set tags" in withData(new CaseFixture()) { c =>
      val caseId = c.id

      service.getCaseTags(Set(caseId)).serviceValue mustBe Map.empty

      val tag1 = CaseTag.Antisocial
      val tag2 = CaseTag.Bullying
      val tag3 = CaseTag.SexualAssault

      service.setCaseTags(caseId, Set(tag1, tag2)).serviceValue mustBe Set(tag1, tag2)
      service.getCaseTags(Set(caseId)).serviceValue mustBe Map(
        caseId -> Set(tag1, tag2)
      )

      service.setCaseTags(caseId, Set(tag2, tag3)).serviceValue mustBe Set(tag2, tag3)
      service.getCaseTags(Set(caseId)).serviceValue mustBe Map(
        caseId -> Set(tag2, tag3)
      )
    }

    "get and set links" in withData(new CaseFixture()) { c1 =>
      val c2 = service.create(CaseSave(c1), Set(UniversityID("0672089")), Set.empty, Teams.MentalHealth, None, None).serviceValue
      val c3 = service.create(CaseSave(c1), Set(UniversityID("0672089"), UniversityID("0672088")), Set.empty, Teams.MentalHealth, None, None).serviceValue

      service.getLinks(c1.id).serviceValue mustBe ((Nil, Nil))

      service.addLink(CaseLinkType.Related, c1.id, c2.id, CaseNoteSave("c1 is related to c2", Usercode("cuscav"))).serviceValue
      service.addLink(CaseLinkType.Related, c1.id, c3.id, CaseNoteSave("c1 merged to c3", Usercode("cuscav"))).serviceValue
      service.addLink(CaseLinkType.Related, c2.id, c3.id, CaseNoteSave("c2 merged to c2", Usercode("cuscav"))).serviceValue

      val (c1Outgoing, c1Incoming) = service.getLinks(c1.id).serviceValue
      c1Outgoing.size mustBe 2
      c1Outgoing.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c2 } mustBe true
      c1Outgoing.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c3 } mustBe true
      c1Incoming mustBe 'empty

      val (c2Outgoing, c2Incoming) = service.getLinks(c2.id).serviceValue
      c2Outgoing.size mustBe 1
      c2Outgoing.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c2 && l.incoming == c3 } mustBe true
      c2Incoming.size mustBe 1
      c2Incoming.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c2 } mustBe true

      val (c3Outgoing, c3Incoming) = service.getLinks(c3.id).serviceValue
      c3Outgoing mustBe 'empty
      c3Incoming.size mustBe 2
      c3Incoming.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c3 } mustBe true
      c3Incoming.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c2 && l.incoming == c3 } mustBe true
    }

    "get and set case notes" in withData(new CaseFixture()) { c =>
      service.getNotes(c.id).serviceValue mustBe 'empty

      val n1 = service.addGeneralNote(c.id, CaseNoteSave(
        text = "I just called to say I love you",
        teamMember = Usercode("cuscav")
      )).serviceValue

      val n2 = service.addGeneralNote(c.id, CaseNoteSave(
        text = "Jim came in to tell me that Peter needed a chat",
        teamMember = Usercode("cusebr")
      )).serviceValue

      service.getNotes(c.id).serviceValue mustBe Seq(n2, n1) // Newest first

      val n1Updated = service.updateNote(c.id, n1.id, CaseNoteSave(
        text = "Jim's not really bothered",
        teamMember = Usercode("cusebr")
      ), n1.lastUpdated).serviceValue

      service.getNotes(c.id).serviceValue mustBe Seq(n2, n1Updated)

      service.deleteNote(c.id, n2.id, n2.lastUpdated).serviceValue mustBe Done

      service.getNotes(c.id).serviceValue mustBe Seq(n1Updated)
    }

    "update" in withData(new CaseFixture()) { c1 =>
      service.getClients(c1.id).serviceValue.map(_.universityID) mustBe Set(UniversityID("3344556"))
      service.getCaseTags(c1.id).serviceValue mustBe 'empty

      // Just add some clients and tags, it's all the same except with a new version
      val c2 = service.update(c1.id, CaseSave(c1), Set(UniversityID("0672089"), UniversityID("0672088")), Set(CaseTag.Accommodation, CaseTag.DomesticViolence), None, c1.lastUpdated).serviceValue
      c2 mustBe c1.copy(lastUpdated = c2.lastUpdated)

      service.getClients(c1.id).serviceValue.exists(_.universityID == UniversityID("0672089")) mustBe true
      service.getClients(c1.id).serviceValue.exists(_.universityID == UniversityID("0672088")) mustBe true
      service.getCaseTags(c1.id).serviceValue mustBe Set(CaseTag.Accommodation, CaseTag.DomesticViolence)

      // Replace a client and a tag and update the subject
      val c3 = service.update(c2.id, CaseSave(c2).copy(subject = "Here's an updated subject"), Set(UniversityID("0672089"), UniversityID("1234567")), Set(CaseTag.Accommodation, CaseTag.HomeSickness), None, c2.lastUpdated).serviceValue
      c3.subject mustBe "Here's an updated subject"
      c3 mustBe c2.copy(lastUpdated = c3.lastUpdated, subject = c3.subject)

      service.getClients(c1.id).serviceValue.exists(_.universityID == UniversityID("0672089")) mustBe true
      service.getClients(c1.id).serviceValue.exists(_.universityID == UniversityID("1234567")) mustBe true
      service.getCaseTags(c1.id).serviceValue mustBe Set(CaseTag.Accommodation, CaseTag.HomeSickness)
    }

    "update state" in withData(new CaseFixture()) { c1 =>
      c1.state mustBe IssueState.Open

      val c2 = service.updateState(c1.id, IssueState.Closed, c1.lastUpdated, CaseNoteSave("Case closed", Usercode("cuscav"))).serviceValue
      c2.state mustBe IssueState.Closed

      val c3 = service.updateState(c1.id, IssueState.Reopened, c2.lastUpdated, CaseNoteSave("Case reopened", Usercode("cuscav"))).serviceValue
      c3.state mustBe IssueState.Reopened
    }

    "get and set documents" in withData(new CaseFixture()) { c =>
      implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

      val saved = service.addDocument(
        c.id,
        CaseDocumentSave(CaseDocumentType.SpecificLearningDifficultyDocument, Usercode("cuscav")),
        ByteSource.wrap("I love lamp".getBytes(StandardCharsets.UTF_8)),
        UploadedFileSave("problem.txt", 11, "text/plain"),
        CaseNoteSave("I hate herons", Usercode("cuscav"))
      ).serviceValue
      saved.documentType mustBe CaseDocumentType.SpecificLearningDifficultyDocument
      saved.file.fileName mustBe "problem.txt"
      saved.file.contentLength mustBe 11
      saved.file.contentType mustBe "text/plain"

      objectStorageService.keyExists(saved.file.id.toString) mustBe true
      val byteSource = new ByteSource {
        override def openStream(): InputStream = objectStorageService.fetch(saved.file.id.toString).orNull
      }
      byteSource.isEmpty mustBe false
      byteSource.size() mustBe 11
      byteSource.asCharSource(StandardCharsets.UTF_8).read() mustBe "I love lamp"

      service.getDocuments(c.id).serviceValue mustBe Seq(saved)

      service.deleteDocument(c.id, saved.id, saved.lastUpdated).serviceValue mustBe Done
      service.getDocuments(c.id).serviceValue mustBe Nil

      // Check that the UploadedFile has been deleted as well
      exec(UploadedFileDao.uploadedFiles.table.length.result) mustBe 0
      exec(UploadedFileDao.uploadedFiles.versionsTable.length.result) mustBe 2 // I, D

      // But the file must still exist in the object store
      objectStorageService.keyExists(saved.file.id.toString) mustBe true
    }

    "find recently viewed" in withData(new CaseFixture) { c =>
      implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

      service.findForView(c.key).serviceValue
      service.findForView(c.key).serviceValue
      service.findForView(c.key).serviceValue
      service.findForView(c.key).serviceValue

      service.findRecentlyViewed(Usercode("cuscav"), 5).serviceValue mustBe Seq(c)
    }

    "search" in withData(new CaseFixture) { c =>
      service.search(CaseSearchQuery(query = Some("assessment")), 5).serviceValue mustBe Seq(c)
      // Test prefix searching
      service.search(CaseSearchQuery(query = Some("asse")), 5).serviceValue mustBe Seq(c)
    }
  }
}
