package system

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, TypeLiteral}
import org.quartz.Scheduler
import play.api.libs.concurrent.AkkaGuiceSupport
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import services.healthcheck._

class AppModule extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])

    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[HealthCheck](binder)
    healthchecks.addBinding.to[UptimeHealthCheck]
    healthchecks.addBinding.to[EncryptedObjectStorageHealthCheck]
  }
}
