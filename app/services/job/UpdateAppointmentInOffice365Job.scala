package services.job
import java.time.Instant
import java.util.{Date, UUID}

import _root_.helpers.ServiceResults
import _root_.helpers.ServiceResults.ServiceResult
import _root_.helpers.StringUtils._
import domain.{AppointmentRender, AppointmentState}
import javax.inject.Inject
import org.quartz._
import _root_.helpers.JavaTime
import play.api.Configuration
import play.api.libs.json.{JsNull, JsValue, Json}
import services.office365.Office365CalendarService
import services.{AppointmentService, AuditLogContext, OwnerService}
import warwick.core.Logging
import warwick.office365.O365Service
import warwick.sso.Usercode

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object UpdateAppointmentInOffice365Job {
  object JobDataMapKeys {
    val appointmentId = "appointmentId"
    val owners = "owners"
    val retries = "retries"
  }

  val maxRetries = 2
}

/**
  * Updates an event in a user's Outlook calendar.
  */
@DisallowConcurrentExecution
class UpdateAppointmentInOffice365Job @Inject()(
  scheduler: Scheduler,
  appointmentService: AppointmentService,
  ownerService: OwnerService,
  office365: O365Service,
  config: Configuration
)(implicit ec: ExecutionContext) extends Job with Logging {

  private lazy val domain: String = config.get[String]("domain")

  object OwnerAndOutlookId {
    def fromMap(input: Map[String, String]): Set[OwnerAndOutlookId] =
      input.map { case (usercode, outlookId) => OwnerAndOutlookId(Usercode(usercode), outlookId.maybeText) }.toSet
  }
  case class OwnerAndOutlookId(owner: Usercode, outlookId: Option[String])

  override def execute(context: JobExecutionContext): Unit = {
    implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()

    val dataMap = context.getJobDetail.getJobDataMap
    val appointmentId = UUID.fromString(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId))
    val owners = OwnerAndOutlookId.fromMap(Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners)).as[Map[String, String]])
    val retries = Try(dataMap.getIntegerFromString(UpdateAppointmentInOffice365Job.JobDataMapKeys.retries)).map(_.intValue()).getOrElse(0)

    val result = Await.result(
      appointmentService.findFull(appointmentId).flatMap(_.fold(
        errors => errors.head.cause.map(t => throw t).getOrElse(throw new RuntimeException(errors.head.message)),
        render => {
          if (render.appointment.state == AppointmentState.Cancelled) {
            Future.sequence(owners.toSeq.filter(_.outlookId.nonEmpty).map(remove))
          } else {
            val (currentOwners, noLongerOwners) = owners.partition(o => render.teamMembers.map(_.member.usercode).contains(o.owner))
            val removals = noLongerOwners.filter(_.outlookId.nonEmpty).toSeq.map(remove)

            val (toSave, toUpdate) = currentOwners.partition(_.outlookId.isEmpty)

            val saves = toSave.toSeq.map(o => save(render, o))

            val updates = toUpdate.toSeq.map(o => update(render, o))

            Future.sequence(removals ++ saves ++ updates)
          }
        }
      )),
      Duration.Inf
    )
    val (successful, failed) = result.partition(_.isRight)

    if (successful.nonEmpty) {
      logger.info("%d owners (%s) updated in Office 365 for %s".format(
        successful.size,
        successful.map(_.right.get.owner.string).mkString(", "),
        appointmentId.toString
      ))
    }

    if (failed.nonEmpty) {
      val failedOwners = owners.diff(successful.map(_.right.get).toSet)
      val message = "%d owners failed to update Office 365: %s".format(
        failedOwners.size,
        failed.flatMap(_.left.get.map(_.message)).mkString(", ")
      )
      if (retries <= UpdateAppointmentInOffice365Job.maxRetries) {
        logger.error(s"$message - retrying failed in 30 seconds")
        scheduler.deleteJob(context.getJobDetail.getKey)
        scheduler.scheduleJob(
          JobBuilder.newJob(classOf[UpdateAppointmentInOffice365Job])
            .withIdentity(context.getJobDetail.getKey)
            .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId, appointmentId.toString)
            .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners, Json.stringify(Json.toJson(failedOwners.map(o => o.owner.string -> o.outlookId.getOrElse("")).toMap)))
            .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.retries, (retries + 1).toString)
            .build(),
          TriggerBuilder.newTrigger()
            .startAt(Date.from(Instant.now().plusSeconds(60)))
            .build()
        )
      } else {
        logger.error(s"$message - max retries reached")
      }

    }
  }

  private def save(render: AppointmentRender, owner: OwnerAndOutlookId)(implicit ac: AuditLogContext): Future[ServiceResult[OwnerAndOutlookId]] = {
    office365.postO365(owner.owner.string, "events", toJson(render, domain)).flatMap {
      case Some(responseJson) =>
        (responseJson \ "Id").asOpt[String].map(id =>
          ownerService.setAppointmentOutlookId(render.appointment.id, owner.owner, id).map(_.map(_ => owner))
        ).getOrElse(
          Future.successful(ServiceResults.error(s"Error saving appointment ${render.appointment.id} to Outlook for ${owner.owner.string} - no ID in response"))
        )
      case _ =>
        Future.successful(ServiceResults.error(s"Error saving appointment ${render.appointment.id} to Outlook for ${owner.owner.string} - error parsing JSON from Office 365"))
    }
  }

  private def update(render: AppointmentRender, owner: OwnerAndOutlookId)(implicit ac: AuditLogContext): Future[ServiceResult[OwnerAndOutlookId]] = {
    office365.patchO365(owner.owner.string, s"events/${owner.outlookId.get}", toJson(render, domain)).map {
      case Some(responseJson) =>
        (responseJson \ "Id").asOpt[String].map(_ =>
          ServiceResults.success(owner)
        ).getOrElse(
          ServiceResults.error(s"Error updating appointment ${render.appointment.id.toString} in Outlook for ${owner.owner.string} - no ID in response")
        )
      case _ =>
        ServiceResults.error(s"Error updating appointment ${render.appointment.id} in Outlook for ${owner.owner.string} - error parsing JSON from Office 365")
    }
  }

  private def remove(owner: OwnerAndOutlookId): Future[ServiceResult[OwnerAndOutlookId]] = {
    office365.deleteO365(owner.owner.string, s"events/${owner.outlookId.get}", Json.obj()).map {
      case Some(JsNull) =>
        ServiceResults.success(owner)
      case _ =>
        ServiceResults.error(s"Error deleting appointment from Outlook for ${owner.owner.string} - error parsing JSON from Office 365")
    }
  }

  private def toJson(a: AppointmentRender, domain: String): JsValue = Json.obj(
    "Subject" -> s"Wellbeing Support Service appointment (${a.appointment.key.string})",
    "Body" -> Json.obj(
      "ContentType" -> "Text",
      "Content" -> "You have a %s appointment with %d client%s".format(
        a.appointment.appointmentType.description.toLowerCase,
        a.clients.size,
        if (a.clients.size == 1) "" else "s"
      )
    ),
    "Start" -> Json.obj(
      "DateTime" -> JavaTime.iSO8601DateFormatNoZone.format(a.appointment.start),
      "TimeZone" -> "Europe/London"
    ),
    "End" -> Json.obj(
      "DateTime" -> JavaTime.iSO8601DateFormatNoZone.format(a.appointment.start.plusMinutes(a.appointment.duration.toMinutes)),
      "TimeZone" -> "Europe/London"
    ),
    "WebLink" -> s"https://$domain${controllers.admin.routes.AppointmentController.view(a.appointment.key).url}",
    "Location" -> Json.obj(
      "DisplayName" -> a.room.map(r => s"${r.name}, ${r.building.name}").getOrElse[String](""),
      "Address" -> JsNull
    )
  )
}
