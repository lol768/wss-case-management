package system

import com.google.inject.AbstractModule

class SchedulerConfigModule extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[SchedulerConfiguration]).asEagerSingleton()

}
