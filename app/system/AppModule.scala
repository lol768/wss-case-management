package system

import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.quartz.Scheduler
import services.healthcheck._

class AppModule extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind[Scheduler].toProvider[SchedulerProvider]

    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[HealthCheck](binder)
    healthchecks.addBinding.to[UptimeHealthCheck]
    healthchecks.addBinding.to[EncryptedObjectStorageHealthCheck]
    healthchecks.addBinding.to[OutgoingEmailQueueHealthCheck]
  }
}
