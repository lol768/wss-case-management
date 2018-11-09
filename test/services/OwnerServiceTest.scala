package services

import java.time.{OffsetDateTime, ZonedDateTime}

import domain.IssueState.Open
import domain._
import domain.dao.AbstractDaoTest
import helpers.DataFixture
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, Usercode}

class OwnerServiceTest extends AbstractDaoTest {

  override implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

  private val enquiryService = get[EnquiryService]
  private val ownerService = get[OwnerService]

  val uniId1 = UniversityID("1")

  class EnquiriesFixture extends DataFixture[Unit] {
    override def setup(): Unit = {
      for (_ <- 1 to 10) {
        enquiryService.save(EnquirySave(
          universityID = uniId1,
          subject = "Enquiry",
          team = Teams.WellbeingSupport,
          state = Open
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
        val enquiry = enquiryService.findAllEnquiriesForClient(uniId1).serviceValue.head.enquiry
        val owner1 = Usercode("1234")
        val owner2 = Usercode("2345")
        val owner3 = Usercode("3456")

        val before = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
        val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

        DateTimeUtils.useMockDateTime(before, () => {
          ownerService.setEnquiryOwners(enquiry.id, Set(owner1, owner2)).serviceValue
        })

        val initialOwners = ownerService.getEnquiryOwners(Set(enquiry.id)).serviceValue(enquiry.id)
        initialOwners.size mustBe 2
        initialOwners.contains(Member(owner1, None, OffsetDateTime.ofInstant(before, JavaTime.timeZone))) mustBe true
        initialOwners.contains(Member(owner2, None, OffsetDateTime.ofInstant(before, JavaTime.timeZone))) mustBe true

        DateTimeUtils.useMockDateTime(now, () => {
          ownerService.setEnquiryOwners(enquiry.id, Set(owner2, owner3)).serviceValue
        })

        val updatedOwners = ownerService.getEnquiryOwners(Set(enquiry.id)).serviceValue(enquiry.id)
        updatedOwners.size mustBe 2
        updatedOwners.contains(Member(owner1, None, OffsetDateTime.ofInstant(before, JavaTime.timeZone))) mustBe false
        updatedOwners.contains(Member(owner2, None, OffsetDateTime.ofInstant(before, JavaTime.timeZone))) mustBe true
        updatedOwners.contains(Member(owner3, None, OffsetDateTime.ofInstant(now, JavaTime.timeZone))) mustBe true
      }
    }
  }

}
