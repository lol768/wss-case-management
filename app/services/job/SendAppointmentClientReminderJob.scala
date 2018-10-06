package services.job

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}

import akka.Done
import domain.{Appointment, AppointmentState, AppointmentTypeCategory, IssueKey}
import helpers.JavaTime
import javax.inject.Inject
import org.quartz._
import services.{AppointmentService, AuditLogContext, NotificationService}
import system.Logging
import warwick.core.timing.TimingContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Sends reminders for clients of an accepted appointment.
  */
@PersistJobDataAfterExecution
class SendAppointmentClientReminderJob @Inject()(
  appointments: AppointmentService,
  notificationService: NotificationService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  override def execute(context: JobExecutionContext): Unit = {
    implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()(
      TimingContext.none // TODO could provide a real context per job run, to track sluggish jobs
    )

    def rescheduleFor(startTime: Instant): Unit = {
      val trigger =
        TriggerBuilder.newTrigger()
          .withIdentity(context.getTrigger.getKey)
          .startAt(Date.from(startTime))
          .build()

      scheduler.rescheduleJob(context.getTrigger.getKey, trigger)
    }

    val dataMap = context.getJobDetail.getJobDataMap
    val id = UUID.fromString(dataMap.getString("id"))
    try {
      Await.result(
        appointments.find(id).flatMap {
          case Left(_) =>
            logger.info(s"Appointment $id no longer exists - ignoring")
            Future.successful(Done)

          case Right(appointment) if appointment.state != AppointmentState.Accepted =>
            logger.info(s"Appointment $id is not accepted (${appointment.state}) - ignoring")
            Future.successful(Done)

          case Right(appointment) if appointment.start.toLocalDate != JavaTime.localDate.plusDays(1) =>
            logger.info(s"Appointment $id is not happening tomorrow (${appointment.start}) - ignoring")
            Future.successful(Done)

          case Right(appointment) if appointment.appointmentType.category != AppointmentTypeCategory.FaceToFace =>
            logger.info(s"Appointment $id is not a face-to-face appointment (${appointment.appointmentType}) - ignoring")
            Future.successful(Done)

          case Right(appointment) =>
            appointments.sendClientReminder(appointment.id).map(_.fold(
              errors => {
                val throwable = errors.flatMap(_.cause).headOption
                logger.error(s"Error sending appointment reminder $id: ${errors.mkString(", ")}", throwable.orNull)
                rescheduleFor(Instant.now().plus(10, ChronoUnit.MINUTES))
                Done
              },
              _ => {}
            ))
        }, Duration.Inf)
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending appointment reminder $id - retrying in 10 minutes", t)
        rescheduleFor(Instant.now().plus(10, ChronoUnit.MINUTES))
    }
  }

}
