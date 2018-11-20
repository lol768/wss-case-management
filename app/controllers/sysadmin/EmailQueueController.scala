package controllers.sysadmin

import java.util.UUID

import controllers.BaseController
import domain.OutgoingEmail
import javax.inject.{Inject, Singleton}
import org.quartz._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.job.SendOutgoingEmailJob
import services.{EmailService, SecurityService}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

@Singleton
class EmailQueueController @Inject()(
  emailService: EmailService,
  scheduler: Scheduler,
  securityService: SecurityService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private def triggerStateForEmail(email: OutgoingEmail): Option[Trigger.TriggerState] = {
    val id = email.id.get.toString
    val identity = new JobKey(id, "SendOutgoingEmail")
    scheduler.getTriggersOfJob(identity).asScala.headOption.map { t => scheduler.getTriggerState(t.getKey) }
  }

  def queued(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    emailService.allUnsentEmails().successMap { emails =>
      Ok(views.html.sysadmin.emailQueue(
        emails.map { email =>
          (email, triggerStateForEmail(email))
        }
      ))
    }
  }

  def enqueue(id: UUID): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    emailService.get(id).successMap {
      case None => // email doesn't exist
        Redirect(controllers.sysadmin.routes.EmailQueueController.queued())

      case Some(email) if triggerStateForEmail(email).nonEmpty => // existing trigger
        Redirect(controllers.sysadmin.routes.EmailQueueController.queued())

      case Some(_) =>
        val key = new JobKey(id.toString, "SendOutgoingEmail")
        logger.info(s"Scheduling job with key $key")

        scheduler.scheduleJob(
          JobBuilder.newJob(classOf[SendOutgoingEmailJob])
            .withIdentity(key)
            .usingJobData("id", id.toString)
            .build(),
          TriggerBuilder.newTrigger()
            .startNow()
            .build()
        )

        Redirect(controllers.sysadmin.routes.EmailQueueController.queued())
          .flashing("success" -> Messages("flash.email.queued"))
    }
  }

}
