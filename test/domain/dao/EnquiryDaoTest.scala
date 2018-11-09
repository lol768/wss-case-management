package domain.dao

import java.util.UUID

import domain._
import slick.dbio.DBIOAction
import warwick.sso.UniversityID
import domain.ExtendedPostgresProfile.api._
import domain.IssueState.Open
import domain.dao.ClientDao.StoredClient
import domain.dao.EnquiryDao.StoredEnquiry

import scala.concurrent.Future

class EnquiryDaoTest extends AbstractDaoTest {

  private val dao = get[EnquiryDao]

  val newEnquiry = StoredEnquiry(
    id = UUID.randomUUID(),
    key = IssueKey(IssueKeyType.Enquiry, 1234),
    universityID = UniversityID("0123456"),
    subject = "Please help",
    team = Teams.WellbeingSupport,
    state = Open
  )

  "EnquiryDao" should {
    "save enquiry objects" in {
      val enquiryWithIdAndKey = newEnquiry.copy(id = UUID.randomUUID(), key = IssueKey(IssueKeyType.Enquiry, 1234))

      val test = for {
        _ <- ClientDao.clients += StoredClient(enquiryWithIdAndKey.universityID, None)
        inserted <- dao.insert(enquiryWithIdAndKey)
        tableSize <- EnquiryDao.enquiries.table.length.result
        versionTableSize <- EnquiryDao.enquiries.versionsTable.length.result
        _ <- DBIOAction.from(Future {
          inserted.id mustBe enquiryWithIdAndKey.id
          inserted.key mustBe enquiryWithIdAndKey.key
          tableSize mustBe 1
          versionTableSize mustBe 1
        })
      } yield inserted

      exec(test)
    }

    // a test for the rollback function more than anything
    "not find objects leaked from other tests" in {
      exec(EnquiryDao.enquiries.table.length.result) mustBe 0
      exec(EnquiryDao.enquiries.versionsTable.length.result) mustBe 0
    }

  }
}
