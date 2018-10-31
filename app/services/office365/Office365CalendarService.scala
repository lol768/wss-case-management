package services.office365

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AppointmentTeamMember, Owner}
import javax.inject.Inject
import org.quartz.{JobBuilder, JobKey, Scheduler, TriggerBuilder}
import play.api.Configuration
import play.api.libs.json.Json
import services.UpdateDifferencesResult
import services.job.UpdateAppointmentInOffice365Job
import warwick.core.Logging
import warwick.core.timing.TimingContext

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[Office365CalendarServiceImpl])
trait Office365CalendarService {
  def updateAppointment(appointmentId: UUID, setOwnersResult: UpdateDifferencesResult[Owner])(implicit t: TimingContext): Unit
  def updateAppointment(appointmentId: UUID, teamMembers: Set[AppointmentTeamMember])(implicit t: TimingContext): Unit
}

class Office365CalendarServiceImpl @Inject()(
  scheduler: Scheduler,
  config: Configuration
)(implicit ec: ExecutionContext) extends Office365CalendarService with Logging {

  private lazy val pushEnabled = config.get[Boolean]("wellbeing.features.pushAppointmentsToOffice365")

  private def update(appointmentId: UUID, ownerMap: Map[String, String])(implicit t: TimingContext): Unit = {
    val key = new JobKey(appointmentId.toString, "UpdateAppointmentInOffice365")

    val owners = ownerMap ++ {
      // If there's an existing job for this appointment grab the owners and then remove the job
      if (scheduler.checkExists(key)) {
        val existingDataMap = scheduler.getJobDetail(key).getJobDataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners)
        scheduler.deleteJob(key)
        Json.parse(existingDataMap).as[Map[String, String]]
      } else {
        Map()
      }
    }

    logger.info(s"Scheduling job with key $key")

    scheduler.scheduleJob(
      JobBuilder.newJob(classOf[UpdateAppointmentInOffice365Job])
        .withIdentity(key)
        .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId, appointmentId.toString)
        .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners, Json.stringify(Json.toJson(owners)))
        .build(),
      TriggerBuilder.newTrigger()
        .startNow()
        .build()
    )
  }

  override def updateAppointment(appointmentId: UUID, setOwnersResult: UpdateDifferencesResult[Owner])(implicit t: TimingContext): Unit =
    if (pushEnabled) update(appointmentId, (setOwnersResult.all ++ setOwnersResult.removed).map(o => o.userId.string -> o.outlookId.getOrElse("")).toMap)

  override def updateAppointment(appointmentId: UUID, teamMembers: Set[AppointmentTeamMember])(implicit t: TimingContext): Unit =
    if (pushEnabled) update(appointmentId, teamMembers.map(m => m.member.usercode.string -> m.outlookId.getOrElse("")).toMap)

}
