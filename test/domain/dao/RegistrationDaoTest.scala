package domain.dao

import java.time.ZonedDateTime

import domain._
import helpers.{MorePatience, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.sso.UniversityID

class RegistrationDaoTest extends AbstractDaoTest {

  private val dao = get[RegistrationDao]

  "RegistrationDao" should {

    "save registrations" in {

      dao.getStudentSupport(UniversityID("1234567")).futureValue

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

      dao.save(registration1).futureValue

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

      dao.save(registration2).futureValue

      val result = dao.getStudentSupport(UniversityID("1234567")).futureValue
      result.nonEmpty must be (true)
      result.get.universityID must be (registration1.universityID)
      result.get.updatedDate must be (registration1.updatedDate)
      result.get.data must be (registration1.data)
    }

  }
}
