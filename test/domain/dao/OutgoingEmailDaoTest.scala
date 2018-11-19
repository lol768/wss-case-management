package domain.dao

import java.time.{Clock, ZonedDateTime}

import domain._
import play.api.libs.json.Json
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

    "insert multiple emails" in {
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val recipients = Stream(Usercode("cuscav"), Usercode("cusebr"), Usercode("cusfal"), Usercode("cusjau"), Usercode("cuskak"))

      val emails = recipients.map { usercode =>
        OutgoingEmail(
          id = None, // Allow the DAO to set this
          email = email,
          recipient = Some(usercode)
        )
      }

      val inserted = exec(dao.insertAll(emails))
      inserted.size mustBe recipients.size
      inserted.foreach { e =>
        e.id mustNot be(null)
        e.email mustBe Json.obj(
          "subject" -> "Here's a lovely email",
          "from" -> "no-reply@warwick.ac.uk",
          "to" -> Seq.empty[String],
          "bodyText" -> "Love it",
          "cc" -> Seq.empty[String],
          "bcc" -> Seq.empty[String],
          "replyTo" -> Seq.empty[String],
          "attachments" -> Seq.empty[String],
          "headers" -> Seq.empty[String],
        )
        recipients.contains(e.recipient.get) mustBe true
        e.emailAddress mustBe 'empty
        e.sent mustBe 'empty
        e.lastSendAttempt mustBe 'empty
        e.failureReason mustBe 'empty
      }
      recipients.forall(inserted.map(_.recipient.get).contains) mustBe true
    }
  }
}
