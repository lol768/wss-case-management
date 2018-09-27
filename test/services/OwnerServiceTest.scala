package services

import java.time.ZonedDateTime

import domain._
import domain.dao.{AbstractDaoTest, DaoRunner}
import helpers.{DataFixture, JavaTime}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.{UniversityID, Usercode}

import scala.util.Random

class OwnerServiceTest extends AbstractDaoTest {

  override implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

  override def fakeApplicationBuilder: GuiceApplicationBuilder =
    super.fakeApplicationBuilder
      .overrides(
        bind[NotificationService].to[NullNotificationService]
      )

  private val enquiryService = get[EnquiryService]
  private val ownerService = get[OwnerService]
  private val runner = get[DaoRunner]

  import profile.api._

  val uniId1 = UniversityID("1")

  class EnquiriesFixture extends DataFixture[Unit] {
    override def setup(): Unit = {
      for (_ <- 1 to 10) {
        val enquiryDate = JavaTime.offsetDateTime.minusDays(10L).plusHours(Random.nextInt(24 * 20).toLong)
        val enquiry = enquiryService.save(Enquiry(
          universityID = uniId1,
          subject = "Enquiry",
          team = Teams.WellbeingSupport,
          version = enquiryDate,
          created = enquiryDate
        ), MessageSave(
          "Hello", MessageSender.Client, None
        ), Nil).serviceValue
      }
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "saving owners" should {
    "persist enquiry owners correctly" in {
      withData(new EnquiriesFixture) { _ =>
        val enquiry = enquiryService.findEnquiriesForClient(uniId1).serviceValue.head.enquiry
        val owner1 = Usercode("1234")
        val owner2 = Usercode("2345")
        val owner3 = Usercode("3456")

        val before = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
        val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

        DateTimeUtils.useMockDateTime(before, () => {
          ownerService.setEnquiryOwners(enquiry.id.get, Set(owner1, owner2)).serviceValue
        })

        val initialOwners = ownerService.getEnquiryOwners(Set(enquiry.id.get)).serviceValue(enquiry.id.get)
        initialOwners.size mustBe 2
        initialOwners.contains(owner1) mustBe true
        initialOwners.contains(owner2) mustBe true

        DateTimeUtils.useMockDateTime(now, () => {
          ownerService.setEnquiryOwners(enquiry.id.get, Set(owner2, owner3)).serviceValue
        })

        val updatedOwners = ownerService.getEnquiryOwners(Set(enquiry.id.get)).serviceValue(enquiry.id.get)
        updatedOwners.size mustBe 2
        updatedOwners.contains(owner1) mustBe false
        updatedOwners.contains(owner2) mustBe true
        updatedOwners.contains(owner3) mustBe true
      }
    }
  }

}
