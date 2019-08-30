package domain.dao

import java.time.{Clock, ZonedDateTime}

import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.ClientDao.StoredClient
import domain.dao.ClientSummaryDao.StoredClientSummary
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

import scala.concurrent.Future

class ClientSummaryDaoTest extends AbstractDaoTest {

  private val dao = get[ClientSummaryDao]

  val uniID = UniversityID("1234567")
  val summary = StoredClientSummary(
    universityID = uniID,
    notes = "Some guy doing something\n\ngood for him",
    alternativeContactNumber = "07777123456",
    alternativeEmailAddress = "nobody@example.com",
    riskStatus = Some(ClientRiskStatus.Medium),
    reasonableAdjustmentsNotes = "",
    initialConsultation = None,
  )

  "ClientSummaryDao" should {

    "save client summary" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val test = for {
          _ <- ClientDao.clients.table += StoredClient(summary.universityID, None, JavaTime.offsetDateTime)
          existsBefore <- dao.get(uniID)
          result <- dao.insert(summary)
          existsAfter <- dao.get(uniID)
          _ <- DBIO.successful {
            existsBefore.isEmpty mustBe true

            result.universityID mustBe uniID
            result.version.toInstant.equals(now) mustBe true
            result.notes mustBe summary.notes
            result.alternativeContactNumber mustBe summary.alternativeContactNumber
            result.alternativeEmailAddress mustBe summary.alternativeEmailAddress
            result.riskStatus mustBe summary.riskStatus
            result.reasonableAdjustmentsNotes mustBe summary.reasonableAdjustmentsNotes
            result.initialConsultation mustBe summary.initialConsultation

            existsAfter.isEmpty mustBe false
            existsAfter.get._1 mustBe result
          }
        } yield result

        exec(test)
      })
    }

    "update client summary" in {
      val updatedSummary = summary.copy(
        notes = "Ah okay then.",
        alternativeContactNumber = "0181 811 8181",
        alternativeEmailAddress = "other@something-else.com",
        riskStatus = Some(ClientRiskStatus.High),
        reasonableAdjustmentsNotes = "Some notes",
        initialConsultation = Some(InitialConsultation(
          reason = "My face hurts",
          suggestedResolution = "Stroke my face gently",
          alreadyTried = "Punching myself in the face"
        ))
      )

      val earlier = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

      val test = for {
        _ <- DBIO.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        }
        _ <- ClientDao.clients.table += StoredClient(summary.universityID, None, JavaTime.offsetDateTime)
        inserted <- dao.insert(summary)
        _ <- DBIO.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        }
        updated <- dao.update(updatedSummary, inserted.version)
        _ <- DBIO.successful {
          updated.universityID mustBe uniID
          updated.version.toInstant.equals(now) mustBe true
          updated.notes mustBe updatedSummary.notes
          updated.alternativeContactNumber mustBe updatedSummary.alternativeContactNumber
          updated.alternativeEmailAddress mustBe updatedSummary.alternativeEmailAddress
          updated.riskStatus mustBe updatedSummary.riskStatus
          updated.reasonableAdjustmentsNotes mustBe updatedSummary.reasonableAdjustmentsNotes
          updated.initialConsultation mustBe updatedSummary.initialConsultation
        }
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

  }
}
