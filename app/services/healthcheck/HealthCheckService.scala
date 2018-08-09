package services.healthcheck

import java.time.LocalDateTime

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import helpers.JavaTime
import system.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object HealthCheckService {
  val frequency = 20.seconds
  val timeout = 5.seconds
}

@Singleton
class HealthCheckService @Inject()(system: ActorSystem) extends Logging {

  import HealthCheckService._

  var healthCheckLastRunAt: LocalDateTime = _

  def runNow(): Unit = {
    healthCheckLastRunAt = JavaTime.localDateTime
  }

  runNow()
  system.scheduler.schedule(frequency, frequency)(runNow())

}