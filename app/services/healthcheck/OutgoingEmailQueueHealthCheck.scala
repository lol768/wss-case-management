package services.healthcheck

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import com.google.inject.Inject
import javax.inject.Singleton
import services.EmailService
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext

import scala.concurrent.Await
import scala.concurrent.duration._

@Singleton
class OutgoingEmailQueueHealthCheck @Inject()(
  emailService: EmailService,
  system: ActorSystem,
) extends NumericHealthCheck[Int]("outgoing-email-queue") with Logging {

  // No timing
  private implicit def timingContext: TimingContext = TimingContext.none

  override def value: Int = Await.result(emailService.countUnsentEmails(), Duration.Inf).right.getOrElse(Int.MaxValue)
  override val warning: Int = 50
  override val critical: Int = 100
  override def message: String = s"$value unsent emails in outgoing email queue (warning: $warning, critical: $critical)"
  override def testedAt: OffsetDateTime = JavaTime.offsetDateTime

  import system.dispatcher
  system.scheduler.schedule(0.seconds, interval = 1.minute) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }

}
