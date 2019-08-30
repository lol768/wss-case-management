package domain.dao

import java.time.{Clock, ZoneId, ZonedDateTime}

import domain.{Disabilities, Medications, RegistrationData, RegistrationReferrals}
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

import scala.concurrent.Future

class RegistrationDaoTest extends AbstractDaoTest {

  private val dao = get[RegistrationDao]

  "RegistrationDao" should {

    "invite" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val uniID = UniversityID("1234567")

        val test = for {
          result <- dao.invite(uniID)
          _ <- DBIO.successful {
            result.universityID mustBe uniID
            result.lastInvited.toInstant.equals(now) mustBe true
            result.parsed.data.isEmpty mustBe true
            result.version.toInstant.equals(now) mustBe true
          }
        } yield result

        exec(test)
      })
    }

    "update registration" in {
      val uniID = UniversityID("1234567")

      val data = RegistrationData(
        gp = "Some guy",
        tutor = "Someone else",
        disabilities = Set(Disabilities.Deaf, Disabilities.Blind),
        medications = Set(Medications.Antidepressant),
        appointmentAdjustments = "None",
        referrals = Set(RegistrationReferrals.GP),
        consentPrivacyStatement = Some(true)
      )

      val updatedData = data.copy(
        gp = "Some other guy",
        tutor = "Someone else again",
        disabilities = Set(),
        medications = Set(),
        appointmentAdjustments = "Some",
        referrals = Set()
      )

      val earlier = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

      val test = for {
        _ <- DBIO.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, ZoneId.systemDefault)
        }
        inserted <- dao.invite(uniID)
        _ <- DBIO.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, ZoneId.systemDefault)
        }
        updated <- dao.update(uniID, Some(updatedData), inserted.lastInvited, inserted.version)
        _ <- DBIO.successful {
          updated.universityID mustBe uniID
          updated.lastInvited.equals(inserted.lastInvited) mustBe true
          updated.version.toInstant.equals(now) mustBe true
          updated.parsed.data.get.gp mustBe updatedData.gp
          updated.parsed.data.get.tutor mustBe updatedData.tutor
          updated.parsed.data.get.disabilities mustBe updatedData.disabilities
          updated.parsed.data.get.medications mustBe updatedData.medications
          updated.parsed.data.get.appointmentAdjustments mustBe updatedData.appointmentAdjustments
          updated.parsed.data.get.referrals mustBe updatedData.referrals
        }
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

  }
}
