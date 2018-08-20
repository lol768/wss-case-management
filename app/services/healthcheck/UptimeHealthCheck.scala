package services.healthcheck

import akka.actor.ActorSystem
import helpers.JavaTime
import javax.inject.{Inject, Singleton}

@Singleton
class UptimeHealthCheck @Inject()(
  system: ActorSystem
) extends NumericHealthCheck[Long] {

  override def name = "uptime"
  override def value = system.uptime
  override def warning = -1
  override def critical = -2
  override def message = s"System has been up for $value seconds"
  override def testedAt = JavaTime.offsetDateTime

}
