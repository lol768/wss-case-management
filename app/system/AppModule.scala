package system

import controllers.UploadedFileErrorProviderImpl
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.quartz.Scheduler
import play.api.{Configuration, Environment}
import services.healthcheck._
import uk.ac.warwick.util.core.scheduling.QuartzDAO
import uk.ac.warwick.util.service.ServiceHealthcheckProvider
import warwick.fileuploads.UploadedFileErrorProvider
import warwick.healthcheck.dao.SlickQuartzDAO

class AppModule(environment: Environment, configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind[Scheduler].toProvider[SchedulerProvider]
    bind[AppStartup].asEagerSingleton()
    bind[UploadedFileErrorProvider].to[UploadedFileErrorProviderImpl]
    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[ServiceHealthcheckProvider](binder)
  
    if (!(configuration.getOptional[Boolean]("healthchecks.disabled")).getOrElse(false)) {
      // we don't need to bind all these for tests
      healthchecks.addBinding.to[UptimeHealthCheck]
      healthchecks.addBinding.to[EncryptedObjectStorageHealthCheck]
      healthchecks.addBinding.to[OutgoingEmailQueueHealthCheck]
      healthchecks.addBinding.to[OutgoingEmailDelayHealthCheck]
      healthchecks.addBinding.to[VirusScanServiceHealthCheck]
    }

    healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck("default"))
    configuration.get[Configuration]("threads").subKeys.toSeq.foreach { name =>
      healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck(name))
    }

    // For HealthCheckService
    bind[QuartzDAO].to[SlickQuartzDAO]
  }
}
