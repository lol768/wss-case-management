package domain.dao

import java.time.{Clock, ZonedDateTime}

import domain._
import helpers.JavaTime
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.UniversityID

import scala.concurrent.Future

class ClientSummaryDaoTest extends AbstractDaoTest {

  private val dao = get[ClientSummaryDao]

  "ClientSummaryDao" should {

    "save client summary" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val uniID = UniversityID("1234567")

        val data = ClientSummaryData(
          highMentalHealthRisk = Some(false),
          fields = ClientSummaryFields(
            notes = "Some guy doing something\n\ngood for him",
            alternativeContactNumber = "07777123456",
            alternativeEmailAddress = "nobody@example.com",
            riskStatus = Some(ClientRiskStatus.Medium),
            reasonableAdjustments = Set(ReasonableAdjustment.Exam5, ReasonableAdjustment.ExtendedDeadlines)
          )
        )

        val test = for {
          existsBefore <- dao.get(uniID)
          result <- dao.insert(uniID, data)
          existsAfter <- dao.get(uniID)
          _ <- DBIO.from(Future {
            existsBefore.isEmpty mustBe true

            result.universityID mustBe uniID
            result.version.toInstant.equals(now) mustBe true
            result.parsed.data.highMentalHealthRisk mustBe data.highMentalHealthRisk
            result.parsed.data.fields.notes mustBe data.fields.notes
            result.parsed.data.fields.alternativeContactNumber mustBe data.fields.alternativeContactNumber
            result.parsed.data.fields.alternativeEmailAddress mustBe data.fields.alternativeEmailAddress
            result.parsed.data.fields.riskStatus mustBe data.fields.riskStatus
            result.parsed.data.fields.reasonableAdjustments mustBe data.fields.reasonableAdjustments

            existsAfter.isEmpty mustBe false
            existsAfter mustBe Some(result)
          })
        } yield result

        exec(test)
      })
    }

    "update client summary" in {
      val uniID = UniversityID("1234567")

      val data = ClientSummaryData(
        highMentalHealthRisk = Some(false),
        fields = ClientSummaryFields(
          notes = "Some guy doing something\n\ngood for him",
          alternativeContactNumber = "07777123456",
          alternativeEmailAddress = "nobody@example.com",
          riskStatus = Some(ClientRiskStatus.Medium),
          reasonableAdjustments = Set(ReasonableAdjustment.Exam5, ReasonableAdjustment.ExtendedDeadlines)
        )
      )

      val updatedData = data.copy(
        highMentalHealthRisk = Some(true),
        fields = ClientSummaryFields(
          notes = "Ah okay then.",
          alternativeContactNumber = "0181 811 8181",
          alternativeEmailAddress = "other@something-else.com",
          riskStatus = Some(ClientRiskStatus.High),
          reasonableAdjustments = Set()
        )
      )

      val earlier = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

      val test = for {
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })
        inserted <- dao.insert(uniID, data)
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })
        updated <- dao.update(uniID, updatedData, inserted.version)
        _ <- DBIO.from(Future {
          updated.universityID mustBe uniID
          updated.version.toInstant.equals(now) mustBe true
          updated.parsed.data.highMentalHealthRisk mustBe updatedData.highMentalHealthRisk
          updated.parsed.data.fields.notes mustBe updatedData.fields.notes
          updated.parsed.data.fields.alternativeContactNumber mustBe updatedData.fields.alternativeContactNumber
          updated.parsed.data.fields.alternativeEmailAddress mustBe updatedData.fields.alternativeEmailAddress
          updated.parsed.data.fields.riskStatus mustBe updatedData.fields.riskStatus
          updated.parsed.data.fields.reasonableAdjustments mustBe updatedData.fields.reasonableAdjustments
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

  }
}
