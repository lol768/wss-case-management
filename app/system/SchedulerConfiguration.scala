package system

import java.util.Date

import com.google.inject.{Inject, Singleton}
import org.quartz.TriggerBuilder._
import org.quartz._
import services.job.UpdateClientsJob

@Singleton
class SchedulerConfiguration @Inject()(
  implicit scheduler: Scheduler
) extends Logging {

  configureScheduledJob(
    "UpdateClientsJob",
    JobBuilder.newJob(classOf[UpdateClientsJob]),
    CronScheduleBuilder.cronSchedule("0 0 */12 * * ?") // Twice a day
  )

  logger.info("Starting the scheduler")
  scheduler.start()

  def configureScheduledJob[SBT <: Trigger](name: String, jobBuilder: JobBuilder, schedule: ScheduleBuilder[SBT])(implicit scheduler: Scheduler): Option[Date] = {
    val jobKey = new JobKey(name)

    if (scheduler.getJobDetail(jobKey) == null) {
      val job = jobBuilder.withIdentity(jobKey).build()
      val trigger = newTrigger().withSchedule[SBT](schedule).build().asInstanceOf[Trigger]

      logger.info(s"Scheduling job: $name")
      Some(scheduler.scheduleJob(job, trigger))
    } else {
      logger.info(s"Job already scheduled: $name")
      None
    }
  }

}
