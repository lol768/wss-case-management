package services.healthcheck

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.ServiceHealthcheck.Status._
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import uk.ac.warwick.util.virusscan.VirusScanService
import warwick.core.Logging
import warwick.core.helpers.JavaTime.{localDateTime => now}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@Singleton
class VirusScanServiceHealthCheck @Inject()(
  virusScanService: VirusScanService,
  system: ActorSystem,
) extends ServiceHealthcheckProvider(new ServiceHealthcheck("virusscan-service", Status.Unknown, now)) with Logging {

  private val name: String = "virusscan-service"

  override def run(): Unit = update({
    Try {
      val startTime = System.currentTimeMillis()
      val status = Await.result(virusScanService.status().toScala, Duration.Inf)

      if (status.isAvailable) {
        val endTime = System.currentTimeMillis()
        val timeTakenMs = endTime - startTime

        new ServiceHealthcheck(
          name,
          Okay,
          now,
          s"Virus scan service available in ${timeTakenMs}ms: ${status.getStatusMessage}",
          Seq[ServiceHealthcheck.PerformanceData[_]](new ServiceHealthcheck.PerformanceData("time_taken_ms", timeTakenMs)).asJava
        )
      } else {
        new ServiceHealthcheck(
          name,
          Error,
          now,
          s"Virus scan service is unavailable: ${status.getStatusMessage}"
        )
      }
    }.recover { case t =>
      new ServiceHealthcheck(
        name,
        Unknown,
        now,
        s"Error performing health check: ${t.getMessage}"
      )
    }.get
  })

  import system.dispatcher
  system.scheduler.schedule(0.seconds, interval = 1.minute) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }

}
