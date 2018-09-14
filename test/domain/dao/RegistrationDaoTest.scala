package domain.dao

import java.time.{Clock, ZoneId, ZonedDateTime}

import domain.{Disabilities, Medications, RegistrationData, RegistrationReferrals}
import helpers.JavaTime
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.UniversityID

import scala.concurrent.Future

class RegistrationDaoTest extends AbstractDaoTest {

  private val dao = get[RegistrationDao]

  "RegistrationDao" should {

    "save registration" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
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

        val test = for {
          exists <- dao.get(uniID)
          result <- dao.insert(uniID, data)
          _ <- DBIO.from(Future {
            exists.isEmpty mustBe true
            result.universityID mustBe uniID
            result.version.toInstant.equals(now) mustBe true
            result.parsed.data.gp mustBe data.gp
            result.parsed.data.tutor mustBe data.tutor
            result.parsed.data.disabilities mustBe data.disabilities
            result.parsed.data.medications mustBe data.medications
            result.parsed.data.appointmentAdjustments mustBe data.appointmentAdjustments
            result.parsed.data.referrals mustBe data.referrals
          })
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
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, ZoneId.systemDefault)
        })
        inserted <- dao.insert(uniID, data)
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, ZoneId.systemDefault)
        })
        updated <- dao.update(uniID, updatedData, inserted.version)
        _ <- DBIO.from(Future {
          updated.universityID mustBe uniID
          updated.version.toInstant.equals(now) mustBe true
          updated.parsed.data.gp mustBe updatedData.gp
          updated.parsed.data.tutor mustBe updatedData.tutor
          updated.parsed.data.disabilities mustBe updatedData.disabilities
          updated.parsed.data.medications mustBe updatedData.medications
          updated.parsed.data.appointmentAdjustments mustBe updatedData.appointmentAdjustments
          updated.parsed.data.referrals mustBe updatedData.referrals
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

  }
}
