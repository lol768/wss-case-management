package services

import akka.Done
import domain.dao.CaseDao.Case
import domain.dao.{AbstractDaoTest, CaseDao}
import domain._
import helpers.DataFixture
import slick.jdbc.PostgresProfile.api._
import warwick.sso.{UniversityID, Usercode}

class CaseServiceTest extends AbstractDaoTest {

  private val service = get[CaseService]

  class CaseFixture extends DataFixture[Case] {
    override def setup(): Case = {
      execWithCommit(CaseDao.cases.insert(
        Fixtures.cases.newCase()
      ))
    }

    override def teardown(): Unit = {
      // FIXME this will get tedious to do separately in every DB test.
      // Need one thing to destroy them all
      execWithCommit(
        CaseDao.caseTags.table.delete andThen
        CaseDao.caseTags.versionsTable.delete andThen
        CaseDao.caseClients.table.delete andThen
        CaseDao.caseClients.versionsTable.delete andThen
        CaseDao.caseLinks.table.delete andThen
        CaseDao.caseLinks.versionsTable.delete andThen
        CaseDao.caseNotes.table.delete andThen
        CaseDao.caseNotes.versionsTable.delete andThen
        CaseDao.cases.table.delete andThen
        CaseDao.cases.versionsTable.delete andThen
        sql"ALTER SEQUENCE SEQ_CASE_ID RESTART WITH 1000".asUpdate
      )
    }
  }

  "CaseServiceTest" should {
    "create" in withData(new CaseFixture()) { _ =>
      val created = service.create(Fixtures.cases.newCase().copy(id = None, key = None), Set(UniversityID("0672089"))).serviceValue
      created.id must not be 'empty
      created.key.map(_.string) mustBe Some("CAS-1000")

      val created2 = service.create(Fixtures.cases.newCase().copy(id = None, key = None), Set(UniversityID("0672089"), UniversityID("0672088"))).serviceValue
      created2.id must not be 'empty
      created2.key.map(_.string) mustBe Some("CAS-1001")
    }

    "find" in withData(new CaseFixture()) { case1 =>
      // Can find by either UUID or IssueKey
      service.find(case1.id.get).serviceValue.id mustBe case1.id
      service.find(case1.key.get).serviceValue.id mustBe case1.id
    }

    "findFull" in withData(new CaseFixture()) { c1 =>
      val clients = Set(UniversityID("0672089"), UniversityID("0672088"))
      val clientCase = service.create(Fixtures.cases.newCase().copy(id = None, key = None), clients).serviceValue
      val tags: Set[CaseTag] = Set(CaseTag.Antisocial, CaseTag.Bullying)

      val c2 = service.create(Fixtures.cases.newCase().copy(id = None, key = None), Set(UniversityID("0672089"), UniversityID("0672088"))).serviceValue

      service.setCaseTags(clientCase.id.get, tags).serviceValue
      service.addLink(CaseLinkType.Related, c1.id.get, clientCase.id.get, CaseNoteSave("c1 is related to clientCase", Usercode("cuscav"))).serviceValue
      service.addLink(CaseLinkType.Related, clientCase.id.get, c2.id.get, CaseNoteSave("clientCase is related to c2", Usercode("cuscav"))).serviceValue

      val fullyJoined = service.findFull(clientCase.key.get).serviceValue
      fullyJoined.clientCase mustBe clientCase
      fullyJoined.clients mustBe clients
      fullyJoined.tags mustBe tags
      fullyJoined.outgoingCaseLinks.size mustBe 1
      fullyJoined.outgoingCaseLinks.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == clientCase && l.incoming == c2 } mustBe true
      fullyJoined.incomingCaseLinks.size mustBe 1
      fullyJoined.incomingCaseLinks.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == clientCase } mustBe true
      fullyJoined.notes.size mustBe 2
      fullyJoined.notes(0).text mustBe "clientCase is related to c2"
      fullyJoined.notes(1).text mustBe "c1 is related to clientCase"
    }

    "get and set tags" in withData(new CaseFixture()) { c =>
      val caseId = c.id.get

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
      val c2 = service.create(Fixtures.cases.newCase().copy(id = None, key = None), Set(UniversityID("0672089"))).serviceValue
      val c3 = service.create(Fixtures.cases.newCase().copy(id = None, key = None), Set(UniversityID("0672089"), UniversityID("0672088"))).serviceValue

      service.getLinks(c1.id.get).serviceValue mustBe ((Nil, Nil))

      service.addLink(CaseLinkType.Related, c1.id.get, c2.id.get, CaseNoteSave("c1 is related to c2", Usercode("cuscav"))).serviceValue
      service.addLink(CaseLinkType.Merge, c1.id.get, c3.id.get, CaseNoteSave("c1 merged to c3", Usercode("cuscav"))).serviceValue
      service.addLink(CaseLinkType.Merge, c2.id.get, c3.id.get, CaseNoteSave("c2 merged to c2", Usercode("cuscav"))).serviceValue

      val (c1Outgoing, c1Incoming) = service.getLinks(c1.id.get).serviceValue
      c1Outgoing.size mustBe 2
      c1Outgoing.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c2 } mustBe true
      c1Outgoing.exists { l => l.linkType == CaseLinkType.Merge && l.outgoing == c1 && l.incoming == c3 } mustBe true
      c1Incoming mustBe 'empty

      val (c2Outgoing, c2Incoming) = service.getLinks(c2.id.get).serviceValue
      c2Outgoing.size mustBe 1
      c2Outgoing.exists { l => l.linkType == CaseLinkType.Merge && l.outgoing == c2 && l.incoming == c3 } mustBe true
      c2Incoming.size mustBe 1
      c2Incoming.exists { l => l.linkType == CaseLinkType.Related && l.outgoing == c1 && l.incoming == c2 } mustBe true

      val (c3Outgoing, c3Incoming) = service.getLinks(c3.id.get).serviceValue
      c3Outgoing mustBe 'empty
      c3Incoming.size mustBe 2
      c3Incoming.exists { l => l.linkType == CaseLinkType.Merge && l.outgoing == c1 && l.incoming == c3 } mustBe true
      c3Incoming.exists { l => l.linkType == CaseLinkType.Merge && l.outgoing == c2 && l.incoming == c3 } mustBe true
    }

    "get and set case notes" in withData(new CaseFixture()) { c =>
      service.getNotes(c.id.get).serviceValue mustBe 'empty

      val n1 = service.addGeneralNote(c.id.get, CaseNoteSave(
        text = "I just called to say I love you",
        teamMember = Usercode("cuscav")
      )).serviceValue

      val n2 = service.addGeneralNote(c.id.get, CaseNoteSave(
        text = "Jim came in to tell me that Peter needed a chat",
        teamMember = Usercode("cusebr")
      )).serviceValue

      service.getNotes(c.id.get).serviceValue mustBe Seq(n2, n1) // Newest first

      val n1Updated = service.updateNote(c.id.get, n1.id, CaseNoteSave(
        text = "Jim's not really bothered",
        teamMember = Usercode("cusebr")
      ), n1.lastUpdated).serviceValue

      service.getNotes(c.id.get).serviceValue mustBe Seq(n2, n1Updated)

      service.deleteNote(c.id.get, n2.id, n2.lastUpdated).serviceValue mustBe Done

      service.getNotes(c.id.get).serviceValue mustBe Seq(n1Updated)
    }

    "update" in withData(new CaseFixture()) { c1 =>
      service.getClients(c1.id.get).serviceValue mustBe 'empty

      // Just add some clients, it's all the same except with a new version
      val c2 = service.update(c1, Set(UniversityID("0672089"), UniversityID("0672088")), c1.version).serviceValue
      c2 mustBe c1.copy(version = c2.version)

      service.getClients(c1.id.get).serviceValue mustBe Set(UniversityID("0672089"), UniversityID("0672088"))

      // Replace a client and update the subject
      val c3 = service.update(c2.copy(subject = "Here's an updated subject"), Set(UniversityID("0672089"), UniversityID("1234567")), c2.version).serviceValue
      c3.subject mustBe "Here's an updated subject"
      c3 mustBe c2.copy(version = c3.version, subject = c3.subject)

      service.getClients(c1.id.get).serviceValue mustBe Set(UniversityID("0672089"), UniversityID("1234567"))
    }

    "update state" in withData(new CaseFixture()) { c1 =>
      c1.state mustBe IssueState.Open

      val c2 = service.updateState(c1.id.get, IssueState.Closed, c1.version, CaseNoteSave("Case closed", Usercode("cuscav"))).serviceValue
      c2.state mustBe IssueState.Closed

      val c3 = service.updateState(c1.id.get, IssueState.Reopened, c2.version, CaseNoteSave("Case reopened", Usercode("cuscav"))).serviceValue
      c3.state mustBe IssueState.Reopened
    }
  }
}
