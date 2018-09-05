package services

import domain.dao.CaseDao.Case
import domain.dao.{AbstractDaoTest, CaseDao}
import domain.{CaseTag, Fixtures}
import helpers.DataFixture
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID

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
  }
}
