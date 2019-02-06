package domain.dao

import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.CaseDao.{CaseFilter, Cases, StoredCase}
import domain.dao.MemberDao.StoredMember
import helpers.DataFixture
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

class CaseDaoTest extends AbstractDaoTest {

  private val dao = get[CaseDao]

  class CasesFixture extends DataFixture[Seq[StoredCase]] {
    override def setup(): Seq[StoredCase] = {

      val case1 = Fixtures.cases.newStoredCase(1)
      val case2 = Fixtures.cases.newStoredCase(2).copy(state = IssueState.Closed)
      val case3 = Fixtures.cases.newStoredCase(3).copy(team = Teams.WellbeingSupport)
      val case4 = Fixtures.cases.newStoredCase(4).copy(team = Teams.WellbeingSupport, state = IssueState.Closed)

      val case5 = Fixtures.cases.newStoredCase(5)
      val case6 = Fixtures.cases.newStoredCase(6).copy(state = IssueState.Closed)
      val case7 = Fixtures.cases.newStoredCase(7).copy(team = Teams.WellbeingSupport)
      val case8 = Fixtures.cases.newStoredCase(8).copy(team = Teams.WellbeingSupport, state = IssueState.Closed)

      val cases = execWithCommit(
        CaseDao.cases.insertAll(Seq(case1, case2, case3, case4, case5, case6, case7, case8))
      )

      execWithCommit(
        MemberDao.members.insert(StoredMember(Usercode("u1234567"), None, JavaTime.offsetDateTime))
      )

      execWithCommit(
        DBIO.sequence(cases.drop(4).map(`case` =>
          Owner.owners.insert(CaseOwner(`case`.id, Usercode("u1234567")))
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
      implicit class QueryTestHelper(query: Query[Cases, StoredCase, Seq]) {
        def toKeys: Seq[Int] = exec(query.result).map(_.key.number)
      }

      dao.listQuery(CaseFilter(Teams.MentalHealth).withState(IssueStateFilter.Open)).toKeys mustBe Seq(1, 5)
      dao.listQuery(CaseFilter(Teams.MentalHealth).withState(IssueStateFilter.Closed)).toKeys mustBe Seq(2, 6)
      dao.listQuery(CaseFilter(Teams.MentalHealth)).toKeys mustBe Seq(1, 2, 5, 6)

      dao.listQuery(CaseFilter(Teams.WellbeingSupport).withState(IssueStateFilter.Open)).toKeys mustBe Seq(3, 7)
      dao.listQuery(CaseFilter(Teams.MentalHealth, Usercode("u1234567")).withState(IssueStateFilter.Open)).toKeys mustBe Seq(5)
      dao.listQuery(CaseFilter(Teams.MentalHealth, Usercode("u1234567"))).toKeys mustBe Seq(5, 6)
      dao.listQuery(CaseFilter(Usercode("u1234567"))).toKeys mustBe Seq(5, 6, 7, 8)
    }
  }

}
