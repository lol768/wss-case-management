package services.healthcheck

import java.time.LocalDateTime

import akka.actor.ActorSystem
import com.google.inject.Inject

class UptimeHealthCheck @Inject()(
  system: ActorSystem
) extends HealthCheck[Long] {

  override def name = "uptime"
  override def value = system.uptime
  override def warning = -1
  override def critical = -2
  override def message = s"System has been up for $value seconds"
  override def testedAt = LocalDateTime.now

}
