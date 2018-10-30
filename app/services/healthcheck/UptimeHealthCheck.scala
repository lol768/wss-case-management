package services.healthcheck

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import warwick.core.Logging
import warwick.core.helpers.JavaTime

import scala.concurrent.duration._

@Singleton
class UptimeHealthCheck @Inject()(
  system: ActorSystem,
) extends NumericHealthCheck[Long]("uptime") with Logging {

  override def value: Long = system.uptime
  override def warning: Long = -1
  override def critical: Long = -2
  override def message = s"System has been up for $value seconds"
  override def testedAt: OffsetDateTime = JavaTime.offsetDateTime

  import system.dispatcher
  system.scheduler.schedule(0.seconds, interval = 5.seconds) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }

}
