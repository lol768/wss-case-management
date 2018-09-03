package services

import domain.Fixtures
import domain.dao.CaseDao.Case
import domain.dao.{AbstractDaoTest, CaseDao}
import helpers.DataFixture
import slick.jdbc.PostgresProfile.api._

class CaseServiceTest extends AbstractDaoTest {

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
        CaseDao.cases.table.delete andThen
        CaseDao.cases.versionsTable.delete
      )
    }
  }

  "CaseServiceTest" should {

    "find" in withData(new CaseFixture()) { case1 =>
      val service = get[CaseService]
      service.find(case1.id.get).serviceValue.id mustBe case1.id
    }

  }
}
