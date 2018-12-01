package system

import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.quartz.Scheduler
import services.healthcheck._
import uk.ac.warwick.util.core.scheduling.QuartzDAO
import uk.ac.warwick.util.service.ServiceHealthcheckProvider
import warwick.healthcheck.dao.SlickQuartzDAO

class AppModule extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind[Scheduler].toProvider[SchedulerProvider]

    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[ServiceHealthcheckProvider](binder)
    healthchecks.addBinding.to[UptimeHealthCheck]
    healthchecks.addBinding.to[EncryptedObjectStorageHealthCheck]
    healthchecks.addBinding.to[OutgoingEmailQueueHealthCheck]
    healthchecks.addBinding.to[OutgoingEmailDelayHealthCheck]
    healthchecks.addBinding.to[DefaultThreadPoolHealthCheck]
    healthchecks.addBinding.to[MailerThreadPoolHealthCheck]
    healthchecks.addBinding.to[ObjectStorageThreadPoolHealthCheck]
    healthchecks.addBinding.to[UserLookupThreadPoolHealthCheck]
    healthchecks.addBinding.to[VirusScanServiceHealthCheck]

    // For HealthCheckService
    bind[QuartzDAO].to[SlickQuartzDAO]
  }
}
