package services

import java.util.Date

import org.quartz.{JobExecutionContext, Scheduler, Trigger, TriggerBuilder}
import warwick.core.helpers.JavaTime

import scala.concurrent.duration.Duration

package object job {

  def rescheduleFor(scheduler: Scheduler, context: JobExecutionContext)(duration: Duration, triggerBuilder: TriggerBuilder[Trigger] => TriggerBuilder[Trigger] = identity): Unit = {
    val trigger =
      triggerBuilder(
        TriggerBuilder.newTrigger()
          .withIdentity(context.getTrigger.getKey)
          .startAt(Date.from(JavaTime.instant.plusSeconds(duration.toSeconds)))
          .usingJobData(context.getTrigger.getJobDataMap)
      ).build()

    scheduler.rescheduleJob(context.getTrigger.getKey, trigger)
  }

}
