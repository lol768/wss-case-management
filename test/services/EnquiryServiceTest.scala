package services

import domain._
import domain.dao.{AbstractDaoTest, DaoRunner}
import helpers.{DataFixture, JavaTime}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.UniversityID

import scala.util.Random

class EnquiryServiceTest extends AbstractDaoTest {

  override def fakeApplicationBuilder: GuiceApplicationBuilder =
    super.fakeApplicationBuilder
      .overrides(
        bind[NotificationService].to[NullNotificationService]
      )

  val enquiryService = get[EnquiryService]
  val runner = get[DaoRunner]

  import profile.api._

  val uniId1 = UniversityID("1")

  class EnquiriesFixture(addMessages: Boolean) extends DataFixture {
    override def setup(): Unit = {
      for (_ <- 1 to 10) {
        val enquiryDate = JavaTime.offsetDateTime.minusDays(10L).plusHours(Random.nextInt(24*20).toLong)
        val enquiry = enquiryService.save(Enquiry(
          universityID = uniId1,
          subject = "Enquiry",
          team = Teams.StudentSupport,
          version = enquiryDate,
          created = enquiryDate
        ), MessageSave(
          "Hello", MessageSender.Client, None
        )).serviceValue

        if (addMessages) {
          for (_ <- 1 to 10) {
            val messageDate = enquiryDate.plusHours(Random.nextInt(1000).toLong)
            DateTimeUtils.useMockDateTime(messageDate.toInstant, () => {
              enquiryService.addMessage(enquiry, MessageSave("Reply!", MessageSender.Team, None))
            })
          }
        }
      }
    }

    override def teardown(): Unit = {
      runner.run(
        Enquiry.enquiries.table.delete andThen
          Enquiry.enquiries.versionsTable.delete andThen
          Message.messages.table.delete andThen
          Message.messages.versionsTable.delete andThen
          Message.messageClients.delete
      ).futureValue
    }
  }

  "querying by client" should {

    "sort enquiries by own version if no other messages" in {
      withData(new EnquiriesFixture(addMessages = false)) {
        val result = enquiryService.findEnquiriesForClient(uniId1).serviceValue
        val enquiries = result.map(_._1)
        val ids = enquiries.map(e => (e.id, e.version)).mkString("\n")
        val idsSorted = enquiries.sortBy(_.version).reverse.map(e => (e.id, e.version)).mkString("\n")
        ids mustBe idsSorted
      }
    }

    "sort enquiries by message version if newer" ignore {
      withData(new EnquiriesFixture(addMessages = true)) {
        val result = enquiryService.findEnquiriesForClient(uniId1).serviceValue
        // TODO check that sorting is as expected (most recent message/enquiry timestamp)
        ???
      }
    }

  }

}
