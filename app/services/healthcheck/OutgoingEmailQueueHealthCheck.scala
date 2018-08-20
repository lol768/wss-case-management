package services.healthcheck

import com.google.inject.Inject
import helpers.JavaTime
import services.EmailService

import scala.concurrent.Await
import scala.concurrent.duration._

class OutgoingEmailQueueHealthCheck @Inject()(
  emailService: EmailService
) extends NumericHealthCheck[Int] {

  override def name = "outgoing-email-queue"
  override def value = Await.result(emailService.countUnsentEmails(), Duration.Inf).right.getOrElse(Int.MaxValue)
  override def warning = 50
  override def critical = 100
  override def message = s"$value items in outgoing email queue"
  override def testedAt = JavaTime.offsetDateTime

}
