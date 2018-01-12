package system

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, TypeLiteral}
import play.api.libs.concurrent.AkkaGuiceSupport
import services.healthcheck._

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val multibinder = Multibinder.newSetBinder(binder(), new TypeLiteral[HealthCheck[_]] {})
    multibinder.addBinding().to(classOf[UptimeHealthCheck])
  }
}
