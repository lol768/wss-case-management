package services.job

import java.util.UUID

import akka.Done
import domain.{AppointmentState, AppointmentType}
import javax.inject.Inject
import org.quartz._
import services.{AppointmentService, AuditLogContext, NotificationService}
import warwick.core.helpers.JavaTime

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Sends reminders for clients of an accepted appointment.
  */
@PersistJobDataAfterExecution
class SendAppointmentClientReminderJob @Inject()(
  appointments: AppointmentService,
  notificationService: NotificationService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override val doLog: Boolean = false

  // Doesn't really matter, not used if doLog = false
  override def getDescription(context: JobExecutionContext): String = "Send Appointment Client Reminder"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    val dataMap = context.getMergedJobDataMap
    val id = UUID.fromString(dataMap.getString("id"))
    try {
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

        case Right(appointment) if appointment.appointmentType != AppointmentType.FaceToFace =>
          logger.info(s"Appointment $id is not a face-to-face appointment (${appointment.appointmentType}) - ignoring")
          Future.successful(Done)

        case Right(appointment) =>
          appointments.sendClientReminder(appointment.id).map(_.fold(
            errors => {
              val throwable = errors.flatMap(_.cause).headOption
              logger.error(s"Error sending appointment reminder $id: ${errors.mkString(", ")}", throwable.orNull)
              rescheduleFor(scheduler, context)(10.minutes)
              Done
            },
            _ => {}
          ))
      }.map(_ => JobResult.quiet)
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending appointment reminder $id - retrying in 10 minutes", t)
        rescheduleFor(scheduler, context)(10.minutes)
        Future.successful(JobResult.quiet)
    }
  }

}
