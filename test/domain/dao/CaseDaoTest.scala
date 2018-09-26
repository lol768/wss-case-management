package domain.dao

import domain._
import domain.dao.CaseDao.{Case, Cases}
import helpers.DataFixture
import warwick.sso.Usercode
import domain.ExtendedPostgresProfile.api._

class CaseDaoTest extends AbstractDaoTest {

  private val dao = get[CaseDao]

  class CasesFixture extends DataFixture[Seq[Case]] {
    override def setup(): Seq[Case] = {

      val case1 = Fixtures.cases.newCase(1)
      val case2 = Fixtures.cases.newCase(2).copy(state = IssueState.Closed)
      val case3 = Fixtures.cases.newCase(3).copy(team = Teams.WellbeingSupport)
      val case4 = Fixtures.cases.newCase(4).copy(team = Teams.WellbeingSupport, state = IssueState.Closed)

      val case5 = Fixtures.cases.newCase(5)
      val case6 = Fixtures.cases.newCase(6).copy(state = IssueState.Closed)
      val case7 = Fixtures.cases.newCase(7).copy(team = Teams.WellbeingSupport)
      val case8 = Fixtures.cases.newCase(8).copy(team = Teams.WellbeingSupport, state = IssueState.Closed)

      val cases = execWithCommit(
        CaseDao.cases.insertAll(Seq(case1, case2, case3, case4, case5, case6, case7, case8))
      )

      execWithCommit(
        DBIO.sequence(cases.drop(4).map(`case` =>
          Owner.owners.insert(CaseOwner(`case`.id.get, Usercode("u1234567")))
        ))
      )

      cases
    }

    override def teardown(): Unit = {
      execWithCommit(
        CaseDao.caseTags.table.delete andThen
        CaseDao.caseTags.versionsTable.delete andThen
        CaseDao.caseClients.table.delete andThen
        CaseDao.caseClients.versionsTable.delete andThen
        CaseDao.cases.table.delete andThen
        CaseDao.cases.versionsTable.delete andThen
        Owner.owners.table.delete andThen
        Owner.owners.versionsTable.delete andThen
        sql"ALTER SEQUENCE SEQ_CASE_ID RESTART WITH 1000".asUpdate
      )
    }
  }

  "CaseDaoTest" should {
    "list cases that match specified filters" in withData(new CasesFixture()) { cases =>
      implicit class QueryTestHelper(query: Query[Cases, Case, Seq]) {
        def toKeys: Seq[Int] = exec(query.result).map(_.key.get.number)
      }

      dao.listQuery(None, None, IssueStateFilter.All).toKeys.length mustBe 8
      dao.listQuery(None, None, IssueStateFilter.Open).toKeys mustBe Seq(1, 3, 5, 7)
      dao.listQuery(None, None, IssueStateFilter.Closed).toKeys mustBe Seq(2, 4, 6, 8)

      dao.listQuery(Some(Teams.MentalHealth), None, IssueStateFilter.Open).toKeys mustBe Seq(1, 5)
      dao.listQuery(Some(Teams.MentalHealth), None, IssueStateFilter.Closed).toKeys mustBe Seq(2, 6)
      dao.listQuery(Some(Teams.MentalHealth), None, IssueStateFilter.All).toKeys mustBe Seq(1, 2, 5, 6)

      dao.listQuery(Some(Teams.WellbeingSupport), None, IssueStateFilter.Open).toKeys mustBe Seq(3, 7)
      dao.listQuery(Some(Teams.MentalHealth), Some(Usercode("u1234567")), IssueStateFilter.Open).toKeys mustBe Seq(5)
      dao.listQuery(Some(Teams.MentalHealth), Some(Usercode("u1234567")), IssueStateFilter.All).toKeys mustBe Seq(5, 6)
      dao.listQuery(None, Some(Usercode("u1234567")), IssueStateFilter.All).toKeys mustBe Seq(5, 6, 7, 8)
    }
  }

}
