package services.job

import java.util.UUID

import akka.Done
import javax.inject.Inject
import org.quartz._
import services.{AuditLogContext, EmailService}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Sends a single outgoing email for a particular user.
  */
@PersistJobDataAfterExecution
class SendOutgoingEmailJob @Inject()(
  emailService: EmailService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override val doLog: Boolean = false

  // Doesn't really matter, not used if doLog = false
  override def getDescription(context: JobExecutionContext): String = "Send Email"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    val dataMap = context.getMergedJobDataMap
    val id = UUID.fromString(dataMap.getString("id"))
    try {
      emailService.get(id).flatMap {
        case Left(_) | Right(None) =>
          logger.info(s"OutgoingEmail $id no longer exists - ignoring")
          Future.successful(Done)

        case Right(Some(email)) =>
          emailService.sendImmediately(email).map(_.fold(
            errors => {
              val throwable = errors.flatMap(_.cause).headOption
              logger.error(s"Error sending email $id: ${errors.mkString(", ")}", throwable.orNull)
              rescheduleFor(scheduler, context)(30.seconds)
              Done
            },
            _ => {}
          ))
      }.map(_ => JobResult.quiet)
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending outgoing email $id - retrying in 30 seconds", t)
        rescheduleFor(scheduler, context)(30.seconds)
        Future.successful(JobResult.quiet)
    }
  }

}
