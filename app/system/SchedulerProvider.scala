package system

import com.google.inject.{Inject, Provider, Singleton}
import org.quartz._
import org.quartz.impl.StdSchedulerFactory
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SchedulerProvider @Inject()(
  jobFactory: GuiceJobFactory,
  lifecycle: ApplicationLifecycle
) extends Provider[Scheduler] {

  def get(): Scheduler = {
    import ExecutionContext.Implicits.global

    val scheduler = new StdSchedulerFactory().getScheduler
    scheduler.setJobFactory(jobFactory)

    def shutdown: Future[Unit] = Future {
      // Waits for running jobs to finish.
      scheduler.shutdown(true)
    }

    lifecycle.addStopHook(shutdown _)

    scheduler
  }
}
