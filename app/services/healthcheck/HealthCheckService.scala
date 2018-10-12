package services.healthcheck

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import helpers.JavaTime
import warwick.core.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object HealthCheckService {
  val frequency = 20.seconds
  val timeout = 5.seconds
}

@Singleton
class HealthCheckService @Inject()(
  system: ActorSystem
)(implicit executionContext: ExecutionContext) extends Logging {

  import HealthCheckService._

  var healthCheckLastRunAt: OffsetDateTime = _

  def runNow(): Unit = {
    healthCheckLastRunAt = JavaTime.offsetDateTime
  }

  runNow()
  system.scheduler.schedule(frequency, frequency)(runNow())

}