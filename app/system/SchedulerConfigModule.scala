package system

import net.codingwell.scalaguice.ScalaModule

class SchedulerConfigModule extends ScalaModule {

  override def configure(): Unit =
    bind[SchedulerConfiguration].asEagerSingleton()

}
