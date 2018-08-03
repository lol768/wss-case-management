package domain.dao

import java.time.ZonedDateTime

import domain._
import helpers.OneAppPerSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.sso.UniversityID

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RegistrationDaoTest extends PlaySpec with MockitoSugar with OneAppPerSuite {

  private val dao = get[RegistrationDao]

  "RegistrationDao" should {

    "save registrations" in {

      Await.result(dao.getStudentSupport(UniversityID("1234567")), Duration.Inf) must be (None)

      val now = ZonedDateTime.now

      val registration1 = Registrations.StudentSupport(
        UniversityID("1234567"),
        now,
        Registrations.StudentSupportData(
          summary = "This summary",
          gp = "The GP",
          tutor = "The tutor",
          disabilities = Set(Disabilities.Blind, Disabilities.Deaf),
          medications = Set(Medications.Antidepressant),
          appointmentAdjustments = "Something",
          referrals = Set(RegistrationReferrals.GP)
        )
      )

      Await.result(dao.save(registration1), Duration.Inf)

      val registration2 = Registrations.StudentSupport(
        UniversityID("1234567"),
        now.minusMinutes(1),
        Registrations.StudentSupportData(
          summary = "Older",
          gp = "The GP",
          tutor = "The tutor",
          disabilities = Set(Disabilities.Blind, Disabilities.Deaf),
          medications = Set(Medications.Antidepressant),
          appointmentAdjustments = "Something",
          referrals = Set(RegistrationReferrals.GP)
        )
      )

      Await.result(dao.save(registration2), Duration.Inf)

      val result = Await.result(dao.getStudentSupport(UniversityID("1234567")), Duration.Inf)
      result.nonEmpty must be (true)
      result.get.universityID must be (registration1.universityID)
      result.get.updatedDate must be (registration1.updatedDate)
      result.get.data must be (registration1.data)
    }

  }
}
