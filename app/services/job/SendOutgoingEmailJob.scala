package services.job

import java.time.Instant
import java.util.{Date, UUID}

import akka.Done
import javax.inject.Inject
import org.quartz._
import services.{AuditLogContext, EmailService}
import warwick.core.Logging
import warwick.core.helpers.JavaTime

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Sends a single outgoing email for a particular user.
  */
@PersistJobDataAfterExecution
class SendOutgoingEmailJob @Inject()(
  emailService: EmailService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  override def execute(context: JobExecutionContext): Unit = {
    implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()

    def rescheduleFor(startTime: Instant): Unit = {
      val trigger =
        TriggerBuilder.newTrigger()
          .startAt(Date.from(startTime))
          .build()

      scheduler.rescheduleJob(context.getTrigger.getKey, trigger)
    }

    val dataMap = context.getJobDetail.getJobDataMap
    val id = UUID.fromString(dataMap.getString("id"))
    try {
      Await.result(
        emailService.get(id).flatMap {
          case Left(_) | Right(None) =>
            logger.info(s"OutgoingEmail $id no longer exists - ignoring")
            Future.successful(Done)

          case Right(Some(email)) =>
            emailService.sendImmediately(email).map(_.fold(
              errors => {
                val throwable = errors.flatMap(_.cause).headOption
                logger.error(s"Error sending email $id: ${errors.mkString(", ")}", throwable.orNull)
                rescheduleFor(JavaTime.instant.plusSeconds(30))
                Done
              },
              _ => {}
            ))
        }, Duration.Inf)
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending outgoing email $id - retrying in 30 seconds", t)
        rescheduleFor(JavaTime.instant.plusSeconds(30))
    }
  }

}
