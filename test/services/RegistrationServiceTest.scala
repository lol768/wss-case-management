package services

import domain.dao.{AbstractDaoTest, RegistrationDao}
import domain._
import helpers.DataFixture
import play.api.libs.json.Json
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

class RegistrationServiceTest extends AbstractDaoTest {

  private val service = get[RegistrationService]

  private val invitedUniID = UniversityID("2345")

  class RegistrationFixture extends DataFixture[Registration] {
    override def setup(): Registration = {
      val now = JavaTime.offsetDateTime
      execWithCommit(
        RegistrationDao.Registration.registrations.insert(RegistrationDao.Registration(
          invitedUniID,
          Json.obj(),
          now
        ))
      ).parsed
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "RegistrationServiceTest" should {

    "invite" in withData(new RegistrationFixture) { _ =>
      val uniID = UniversityID("1234")

      val now = JavaTime.offsetDateTime

      DateTimeUtils.useMockDateTime(now.toInstant, () => {
        val result = service.invite(uniID).serviceValue
        result.universityID mustBe uniID
        result.data mustBe None
        result.lastInvited mustBe now
      })

    }

    "re-invite" in withData(new RegistrationFixture) { _ =>
      val uniID = UniversityID("1234")

      val before = JavaTime.offsetDateTime.minusDays(1)

      DateTimeUtils.useMockDateTime(before.toInstant, () => {
        service.invite(uniID).serviceValue
      })

      val now = JavaTime.offsetDateTime

      DateTimeUtils.useMockDateTime(now.toInstant, () => {
        val result = service.invite(uniID).serviceValue
        result.universityID mustBe uniID
        result.data mustBe None
        result.lastInvited mustBe now
      })

    }

    "register" in withData(new RegistrationFixture) { invited =>
      val data = RegistrationData(
        gp = "Doctor Who",
        tutor = "Some guy",
        disabilities = Set(Disabilities.Deaf),
        medications = Set(Medications.Antipsychotic),
        appointmentAdjustments = "Some",
        referrals = Set(RegistrationReferrals.FamilyMember),
        consentPrivacyStatement = Some(true)
      )
      val result = service.register(invited.universityID, data, invited.updatedDate).serviceValue
      result.universityID mustBe invited.universityID
      result.data.get mustBe data
      result.lastInvited mustBe invited.lastInvited
    }

  }

}
