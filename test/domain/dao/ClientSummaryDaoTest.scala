package domain.dao

import java.time.{Clock, ZonedDateTime}

import domain._
import domain.dao.ClientSummaryDao.PersistedClientSummary
import helpers.JavaTime
import play.api.libs.json.JsObject
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.UniversityID

import scala.concurrent.Future

class ClientSummaryDaoTest extends AbstractDaoTest {

  private val dao = get[ClientSummaryDao]

  val uniID = UniversityID("1234567")
  val data = ClientSummaryData(
    highMentalHealthRisk = Some(false),
    notes = "Some guy doing something\n\ngood for him",
    alternativeContactNumber = "07777123456",
    alternativeEmailAddress = "nobody@example.com",
    riskStatus = Some(ClientRiskStatus.Medium),
    reasonableAdjustments = Set(ReasonableAdjustment.Exam5, ReasonableAdjustment.ExtendedDeadlines)
  )

  "ClientSummaryDao" should {

    "save client summary" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val test = for {
          existsBefore <- dao.get(uniID)
          result <- dao.insert(uniID, data)
          existsAfter <- dao.get(uniID)
          _ <- DBIO.from(Future {
            existsBefore.isEmpty mustBe true

            result.universityID mustBe uniID
            result.version.toInstant.equals(now) mustBe true
            result.parsed.data.highMentalHealthRisk mustBe data.highMentalHealthRisk
            result.parsed.data.notes mustBe data.notes
            result.parsed.data.alternativeContactNumber mustBe data.alternativeContactNumber
            result.parsed.data.alternativeEmailAddress mustBe data.alternativeEmailAddress
            result.parsed.data.riskStatus mustBe data.riskStatus
            result.parsed.data.reasonableAdjustments mustBe data.reasonableAdjustments

            existsAfter.isEmpty mustBe false
            existsAfter mustBe Some(result)
          })
        } yield result

        exec(test)
      })
    }

    "update client summary" in {
      val updatedData = data.copy(
        highMentalHealthRisk = Some(true),
        notes = "Ah okay then.",
        alternativeContactNumber = "0181 811 8181",
        alternativeEmailAddress = "other@something-else.com",
        riskStatus = Some(ClientRiskStatus.High),
        reasonableAdjustments = Set()
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
          updated.parsed.data.notes mustBe updatedData.notes
          updated.parsed.data.alternativeContactNumber mustBe updatedData.alternativeContactNumber
          updated.parsed.data.alternativeEmailAddress mustBe updatedData.alternativeEmailAddress
          updated.parsed.data.riskStatus mustBe updatedData.riskStatus
          updated.parsed.data.reasonableAdjustments mustBe updatedData.reasonableAdjustments
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

    "parse optional fields properly" in {
      // riskStatus was made optional, but the way it's parsed isn't
      val oldJson = data.getJsonFields.as[JsObject] - "riskStatus"
      val oldData = PersistedClientSummary(
        uniID,
        None,
        oldJson,
        JavaTime.offsetDateTime
      )

      oldData.parsed.data.riskStatus mustBe None
    }

  }
}
