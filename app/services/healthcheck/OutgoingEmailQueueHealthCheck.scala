package services.healthcheck

import akka.actor.ActorSystem
import com.google.inject.Inject
import helpers.JavaTime
import services.EmailService
import warwick.core.Logging
import warwick.core.timing.TimingContext

import scala.concurrent.Await
import scala.concurrent.duration._

class OutgoingEmailQueueHealthCheck @Inject()(
  emailService: EmailService,
  system: ActorSystem,
) extends NumericHealthCheck[Int]("outgoing-email-queue") with Logging {

  // No timing
  private implicit def timingContext: TimingContext = TimingContext.none

  override def value = Await.result(emailService.countUnsentEmails(), Duration.Inf).right.getOrElse(Int.MaxValue)
  override def warning = 50
  override def critical = 100
  override def message = s"$value items in outgoing email queue"
  override def testedAt = JavaTime.offsetDateTime

  import system.dispatcher
  system.scheduler.schedule(0.seconds, interval = 1.minute) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }

}
