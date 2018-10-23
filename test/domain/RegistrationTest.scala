package domain

import java.time.OffsetDateTime

import helpers.JavaTime
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}

class RegistrationTest extends PlaySpec {

  "RegistrationDataHistory" should {

    "match the number of fields in RegistrationData" in {
      RegistrationDataHistory(null, null, null, null, null, null, null).productArity mustBe RegistrationData(null, null, null, null, null, null, null).productArity
    }

    "include all fields in the JSON writer" in {
      val d = RegistrationDataHistory(Seq(), Seq(), Seq(), Seq(), Seq(), Seq(), Seq())
      Json.toJson(d)(RegistrationDataHistory.writer)
        .as[JsObject].value.keys.size mustBe d.productArity
    }

    "use id and description in JSON for appropriate fields" in {
      val now = JavaTime.offsetDateTime
      val d = RegistrationDataHistory(
        gp = Seq(("A doctor", now)),
        tutor = Seq(("A tutor", now)),
        disabilities = Seq((Set(Disabilities.Blind), now)),
        medications = Seq((Set(Medications.Antidepressant), now)),
        appointmentAdjustments = Seq(("Adjustment", now)),
        referrals = Seq((Set(RegistrationReferrals.GP), now)),
        consentPrivacyStatement = Seq((Some(true), now))
      )
      val js = Json.toJson(d)(RegistrationDataHistory.writer)
      js mustBe Json.obj(
        "gp" -> Json.arr(Json.obj(
          "value" -> "A doctor",
          "version" -> now
        )),
        "tutor" -> Json.arr(Json.obj(
          "value" -> "A tutor",
          "version" -> now
        )),
        "disabilities" -> Json.arr(Json.obj(
          "value" -> Json.arr(Json.obj(
            "id" -> Disabilities.Blind.id,
            "description" -> Disabilities.Blind.description
          )),
          "version" -> now
        )),
        "medications" -> Json.arr(Json.obj(
          "value" -> Json.arr(Json.obj(
            "id" -> Medications.Antidepressant.id,
            "description" -> Medications.Antidepressant.description
          )),
          "version" -> now
        )),
        "appointmentAdjustments" -> Json.arr(Json.obj(
          "value" -> "Adjustment",
          "version" -> now
        )),
        "referrals" -> Json.arr(Json.obj(
          "value" -> Json.arr(Json.obj(
            "id" -> RegistrationReferrals.GP.id,
            "description" -> RegistrationReferrals.GP.description
          )),
          "version" -> now
        )),
        "consentPrivacyStatement" -> Json.arr(Json.obj(
          "value" -> true,
          "version" -> now
        ))
      )
    }

    "flatten history" in {
      val now = JavaTime.offsetDateTime

      val data1 = RegistrationData(
        gp = "A doctor",
        tutor = "A tutor",
        disabilities = Set(Disabilities.Blind),
        medications = Set(Medications.Antidepressant),
        appointmentAdjustments = "",
        referrals = Set(RegistrationReferrals.GP, RegistrationReferrals.FamilyMember),
        consentPrivacyStatement = Some(true)
      )
      val data1Version = now.minusHours(1)

      val data2 = RegistrationData(
        gp = "A different doctor",
        tutor = "A tutor", // No change from 1
        disabilities = Set(Disabilities.Blind), // No change from 1
        medications = Set(Medications.Antidepressant, Medications.Antipsychotic), // Addition
        appointmentAdjustments = "", // No change from 1
        referrals = Set(RegistrationReferrals.GP), // Removal
        consentPrivacyStatement = Some(true)
      )
      val data2Version = now.minusMinutes(30)

      val data3 = RegistrationData(
        gp = "A different doctor", // No change from 2
        tutor = "A tutor", // No change from 1
        disabilities = Set(Disabilities.Deaf), // Change from 1
        medications = Set(Medications.Antidepressant, Medications.Antipsychotic), // No change from 2
        appointmentAdjustments = "", // No change from 1
        referrals = Set(RegistrationReferrals.GP, RegistrationReferrals.FamilyMember), // Change from 2
        consentPrivacyStatement = Some(true)
      )
      val data3Version = now.minusMinutes(15)

      val result = RegistrationDataHistory.apply(Seq(
        (data1, data1Version),
        (data2, data2Version),
        (data3, data3Version)
      ))

      result.gp.size mustBe 2
      result.gp.head mustBe ((data2.gp, data2Version))
      result.gp(1) mustBe ((data1.gp, data1Version))

      result.tutor.size mustBe 1
      result.tutor.head mustBe ((data1.tutor, data1Version))

      result.disabilities.size mustBe 2
      result.disabilities.head mustBe ((data3.disabilities, data3Version))
      result.disabilities(1) mustBe ((data1.disabilities, data1Version))

      result.medications.size mustBe 2
      result.medications.head mustBe ((data2.medications, data2Version))
      result.medications(1) mustBe ((data1.medications, data1Version))

      result.appointmentAdjustments.size mustBe 1
      result.appointmentAdjustments.head mustBe ((data1.appointmentAdjustments, data1Version))

      result.referrals.size mustBe 3
      result.referrals.head mustBe ((data3.referrals, data3Version))
      result.referrals(1) mustBe ((data2.referrals, data2Version))
      result.referrals(2) mustBe ((data1.referrals, data1Version))
    }

  }

}
