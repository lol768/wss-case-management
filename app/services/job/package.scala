package services

import java.time.Instant
import java.util.Date

import org.quartz.{JobExecutionContext, Scheduler, TriggerBuilder}

package object job {

  def rescheduleFor(scheduler: Scheduler, context: JobExecutionContext)(startTime: Instant): Unit = {
    val trigger =
      TriggerBuilder.newTrigger()
        .withIdentity(context.getTrigger.getKey)
        .startAt(Date.from(startTime))
        .usingJobData(context.getTrigger.getJobDataMap)
        .build()

    scheduler.rescheduleJob(context.getTrigger.getKey, trigger)
  }

}
