package services.job

import java.time.Period
import java.util.UUID

import akka.Done
import domain.{IssueState, MessageSender}
import javax.inject.Inject
import org.quartz._
import services.job.SendEnquiryClientReminderJob._
import services.{AuditLogContext, EnquiryService, NotificationService}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object SendEnquiryClientReminderJob {
  val SendReminderAfter: Period = Period.ofDays(5)
  val SendMessageAs: Usercode = Usercode("system")

  val EnquiryIDJobDataKey: String = "id"
  val IsFinalReminderJobDataKey: String = "isFinalReminder"

  val JobKeyGroup: String = "SendEnquiryClientReminder"
  val TriggerKeyGroup: String = "SendEnquiryClientReminder"

  def jobKey(enquiryID: UUID): JobKey = new JobKey(enquiryID.toString, JobKeyGroup)
  def triggerKey(enquiryID: UUID): TriggerKey = new TriggerKey(enquiryID.toString, TriggerKeyGroup)
}

/**
  * Sends reminders for clients of an enquiry message from the team that they haven't replied to.
  */
@PersistJobDataAfterExecution
class SendEnquiryClientReminderJob @Inject()(
  enquiries: EnquiryService,
  notificationService: NotificationService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override val doLog: Boolean = false

  // Doesn't really matter, not used if doLog = false
  override def getDescription(context: JobExecutionContext): String = "Send Enquiry Client Reminder"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    val dataMap = context.getMergedJobDataMap
    val id = UUID.fromString(dataMap.getString(EnquiryIDJobDataKey))
    val isFinalReminder = dataMap.getBooleanValue(IsFinalReminderJobDataKey)
    try {
      enquiries.getForRender(id).flatMap {
        case Left(_) =>
          logger.info(s"Enquiry $id no longer exists - ignoring")
          Future.successful(Done)

        case Right(r) if r.enquiry.state == IssueState.Closed =>
          logger.info(s"Enquiry $id is closed - ignoring")
          Future.successful(Done)

        case Right(r) if r.messages.last.message.sender == MessageSender.Client =>
          logger.info(s"Enquiry $id's last message was from the client - ignoring")
          Future.successful(Done)

        case Right(r) if r.messages.last.message.created.isAfter(JavaTime.offsetDateTime.minus(SendReminderAfter)) =>
          logger.info(s"Enquiry $id's last message was too recent (${r.messages.last.message.created}) - ignoring")
          Future.successful(Done)

        case Right(r) =>
          enquiries.sendClientReminder(r.enquiry.id, isFinalReminder).map(_.fold(
            errors => {
              val throwable = errors.flatMap(_.cause).headOption
              logger.error(s"Error sending enquiry reminder $id: ${errors.mkString(", ")}", throwable.orNull)
              rescheduleFor(scheduler, context)(10.minutes)
              Done
            },
            _ => {}
          ))
      }.map(_ => JobResult.quiet)
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending enquiry reminder $id - retrying in 10 minutes", t)
        rescheduleFor(scheduler, context)(10.minutes)
        Future.successful(JobResult.quiet)
    }
  }

}
