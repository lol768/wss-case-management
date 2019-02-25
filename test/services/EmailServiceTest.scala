package services

import java.util.UUID

import domain.ExtendedPostgresProfile.api._
import domain.dao.OutgoingEmailDao.PersistedOutgoingEmail
import domain.dao.{DaoRunner, OutgoingEmailDao}
import domain.{Fixtures, OutgoingEmail}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.quartz.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.libs.mailer.{Email, MailerClient}
import warwick.sso.UserLookupService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EmailServiceTest extends PlaySpec with MockitoSugar with ScalaFutures with NoAuditLogging {

  private trait Fixture {
    val mockAuditService: AuditService = mock[AuditService](RETURNS_SMART_NULLS)
    val mockOutgoingEmailDao: OutgoingEmailDao = mock[OutgoingEmailDao](RETURNS_SMART_NULLS)
    val mockDaoRunner: DaoRunner = mock[DaoRunner](RETURNS_SMART_NULLS)
    val mockScheduler: Scheduler = mock[Scheduler](RETURNS_SMART_NULLS)
    val mockUserLookupService: UserLookupService = mock[UserLookupService](RETURNS_SMART_NULLS)
    val mockMailerClient: MailerClient = mock[MailerClient](RETURNS_SMART_NULLS)

    val emailService = new EmailServiceImpl(
      mockAuditService,
      mockOutgoingEmailDao,
      mockDaoRunner,
      mockScheduler,
      mockUserLookupService,
      mockMailerClient,
      ExecutionContext.Implicits.global,
    )
  }

  "EmailService#queue" should {
    "schedule jobs for all recipients" in new Fixture {
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val recipients = Stream(Fixtures.users.ss1, Fixtures.users.ss2, Fixtures.users.mh1, Fixtures.users.mh2)

      val emails = recipients.map { u =>
        OutgoingEmail(
          id = None, // Allow the DAO to set this
          email = email,
          recipient = Some(u.usercode)
        )
      }

      def toPersistedOutgoingEmail(email: OutgoingEmail) =
        PersistedOutgoingEmail(
          UUID.randomUUID(),
          email.created,
          Json.toJson(email.email)(OutgoingEmail.emailFormatter),
          email.recipient,
          email.emailAddress,
          email.sent,
          email.lastSendAttempt,
          email.failureReason
        )

      val persistedEmails = emails.map(toPersistedOutgoingEmail)
      val insertAll = DBIO.successful(persistedEmails)

      when(mockOutgoingEmailDao.insertAll(any())(any())).thenReturn(insertAll)
      when(mockDaoRunner.run(any(classOf[DBIO[Stream[PersistedOutgoingEmail]]]))(any())).thenReturn(Future.successful(persistedEmails))

      emailService.queue(email, recipients).futureValue.isRight mustBe true

      verify(mockScheduler, times(4)).scheduleJob(any(), any())
    }
  }

}
