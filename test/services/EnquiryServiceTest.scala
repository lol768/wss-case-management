package services

import domain.IssueState.Open
import domain._
import domain.dao.AbstractDaoTest
import helpers.{DataFixture, JavaTime}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.{UniversityID, Usercode}

import scala.util.Random

class EnquiryServiceTest extends AbstractDaoTest {

  override implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

  private val enquiryService = get[EnquiryService]

  val uniId1 = UniversityID("1")

  class EnquiriesFixture(addMessages: Boolean) extends DataFixture[Unit] {
    override def setup(): Unit = {
      for (_ <- 1 to 10) {
        val enquiryDate = JavaTime.offsetDateTime.minusDays(10L).plusHours(Random.nextInt(24*20).toLong)
        val enquiry = enquiryService.save(EnquirySave(
          universityID = uniId1,
          subject = "Enquiry",
          team = Teams.WellbeingSupport,
          state = Open
        ), MessageSave(
          "Hello", MessageSender.Client, None
        ), Nil).serviceValue

        if (addMessages) {
          for (_ <- 1 to 10) {
            val messageDate = enquiryDate.plusHours(Random.nextInt(1000).toLong)
            DateTimeUtils.useMockDateTime(messageDate.toInstant, () => {
              enquiryService.addMessage(enquiry, MessageSave("Reply!", MessageSender.Team, None), Nil)
            })
          }
        }
      }
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "querying by client" should {

    "sort enquiries by own version if no other messages" in {
      withData(new EnquiriesFixture(addMessages = false)) { _ =>
        val result = enquiryService.findAllEnquiriesForClient(uniId1).serviceValue
        val enquiries = result.map(_.enquiry)
        val ids = enquiries.map(e => (e.id, e.lastUpdated)).mkString("\n")
        val idsSorted = enquiries.sortBy(_.lastUpdated).reverse.map(e => (e.id, e.lastUpdated)).mkString("\n")
        ids mustBe idsSorted
      }
    }

    "sort enquiries by message version if newer" ignore {
      withData(new EnquiriesFixture(addMessages = true)) { _ =>
        val result = enquiryService.findAllEnquiriesForClient(uniId1).serviceValue
        // TODO check that sorting is as expected (most recent message/enquiry timestamp)
        ???
      }
    }

  }

}
