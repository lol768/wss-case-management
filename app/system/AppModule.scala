package system

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, TypeLiteral}
import org.quartz.Scheduler
import play.api.libs.concurrent.AkkaGuiceSupport
import services.healthcheck._

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])

    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val multibinder = Multibinder.newSetBinder(binder(), new TypeLiteral[HealthCheck[_]] {})
    multibinder.addBinding().to(classOf[UptimeHealthCheck])
  }
}
