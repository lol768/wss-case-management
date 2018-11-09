package domain.dao

import java.time.{Clock, ZonedDateTime}

import domain._
import play.api.libs.mailer.Email
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.Future

class OutgoingEmailDaoTest extends AbstractDaoTest {

  private val dao = get[OutgoingEmailDao]

  "OutgoingEmailDao" should {
    "save an email" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val email = Email(
          subject = "Here's a lovely email",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some("Love it")
        )

        val outgoingEmail = OutgoingEmail(
          email = email,
          recipient = Some(Usercode("cuscav"))
        )

        val test = for {
          result <- dao.insert(outgoingEmail)
          existsAfter <- dao.get(result.id)
          _ <- DBIO.from(Future {
            result.version.toInstant.equals(now) mustBe true

            result.parsed.created.toInstant.equals(now) mustBe true
            result.parsed.email mustBe email
            result.parsed.recipient mustBe outgoingEmail.recipient

            existsAfter.isEmpty mustBe false
            existsAfter mustBe Some(result)
          })
        } yield result

        exec(test)
      })
    }

    "update an email" in {
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val outgoingEmail = OutgoingEmail(
        email = email,
        recipient = Some(Usercode("cuscav"))
      )

      val updatedOutgoingEmail = outgoingEmail.copy(
        emailAddress = Some("m.mannion@warwick.ac.uk")
      )

      val earlier = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

      val test = for {
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })
        inserted <- dao.insert(outgoingEmail)
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })
        updated <- dao.update(updatedOutgoingEmail.copy(id = Some(inserted.id)), inserted.version)
        _ <- DBIO.from(Future {
          updated.id mustBe inserted.id
          updated.version.toInstant.equals(now) mustBe true
          updated.parsed.email mustBe email
          updated.parsed.recipient mustBe outgoingEmail.recipient
          updated.parsed.emailAddress mustBe updatedOutgoingEmail.emailAddress
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

  }
}
