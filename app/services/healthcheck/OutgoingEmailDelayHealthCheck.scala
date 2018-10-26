package services.healthcheck

import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import com.google.inject.Inject
import helpers.JavaTime
import helpers.JavaTime.{localDateTime => now}
import javax.inject.Singleton
import services.EmailService
import services.healthcheck.OutgoingEmailDelayHealthCheck._
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.Logging
import warwick.core.timing.TimingContext

import scala.concurrent.Await
import scala.concurrent.duration.Duration._
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object OutgoingEmailDelayHealthCheck {
  val Name: String = "outgoing-email-delay"
  val WarningThreshold: FiniteDuration = 30.minutes
  val ErrorThreshold: FiniteDuration = 1.hour

  val CheckInterval: FiniteDuration = 1.minute
}

@Singleton
class OutgoingEmailDelayHealthCheck @Inject()(
  emails: EmailService,
  system: ActorSystem,
) extends ServiceHealthcheckProvider(new ServiceHealthcheck(Name, Status.Unknown, now)) with Logging {

  // No timing
  private implicit def timingContext: TimingContext = TimingContext.none

  import system.dispatcher

  override def run(): Unit = {
    // How old is the oldest un-sent item in the queue?
    val oldestUnsentEmailCreatedAgo: FiniteDuration =
      Await.result(
        emails.oldestUnsentEmail().map(_.fold(
          _ => Zero,
          _.fold(Zero) { email =>
            email.created.until(JavaTime.offsetDateTime, ChronoUnit.MINUTES).minutes
          }
        )),
        Inf
      )

    // How new is the latest item in the queue?
    val mostRecentlySentAgo: FiniteDuration =
      Await.result(
        emails.mostRecentlySentEmail().map(_.fold(
          _ => Zero,
          _.flatMap(_.sent).fold(Zero) { sent =>
            sent.until(JavaTime.offsetDateTime, ChronoUnit.MINUTES).minutes
          }
        )),
        Inf
      )

    val status =
      if (oldestUnsentEmailCreatedAgo == Zero) ServiceHealthcheck.Status.Okay // empty queue
      else if (mostRecentlySentAgo >= ErrorThreshold) ServiceHealthcheck.Status.Error
      else if (mostRecentlySentAgo >= WarningThreshold) ServiceHealthcheck.Status.Warning
      else ServiceHealthcheck.Status.Okay // email queue still processing so may take time to sent them all

    update(new ServiceHealthcheck(
      Name,
      status,
      now,
      s"Last sent email $mostRecentlySentAgo ago, oldest unsent email $oldestUnsentEmailCreatedAgo old, (warning: $WarningThreshold, critical: $ErrorThreshold)",
      Seq[ServiceHealthcheck.PerformanceData[_]](
        new ServiceHealthcheck.PerformanceData("oldest_unsent", oldestUnsentEmailCreatedAgo.toMinutes, WarningThreshold.toMinutes, ErrorThreshold.toMinutes),
        new ServiceHealthcheck.PerformanceData("last_sent", mostRecentlySentAgo.toMinutes, WarningThreshold.toMinutes, ErrorThreshold.toMinutes)
      ).asJava
    ))
  }

  system.scheduler.schedule(0.seconds, interval = CheckInterval) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }

}
