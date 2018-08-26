package domain.dao

import java.util.UUID

import domain._
import slick.dbio.DBIOAction
import warwick.sso.UniversityID
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class EnquiryDaoTest extends AbstractDaoTest {

  private val dao = get[EnquiryDao]

  val newEnquiry = Enquiry(
    universityID = UniversityID("0123456"),
    subject = "Please help",
    team = Teams.StudentSupport
  )

  "EnquiryDao" should {
    "fail if UUID missing" in {
      intercept[NoSuchElementException] {
        exec(dao.insert(newEnquiry))
      }
    }

    "save enquiry objects" in {
      val enquiryWithId = newEnquiry.copy(id = Some(UUID.randomUUID()))

      val test = for {
        inserted <- dao.insert(enquiryWithId)
        tableSize <- Enquiry.enquiries.table.length.result
        versionTableSize <- Enquiry.enquiries.versionsTable.length.result
        _ <- DBIOAction.from(Future {
          inserted.id mustBe enquiryWithId.id
          tableSize mustBe 1
          versionTableSize mustBe 1
        })
      } yield inserted

      exec(test)
    }

    // a test for the rollback function more than anything
    "not find objects leaked from other tests" in {
      exec(Enquiry.enquiries.table.length.result) mustBe 0
      exec(Enquiry.enquiries.versionsTable.length.result) mustBe 0
    }

  }
}
