package services.job

import java.util.UUID

import domain.{AppointmentRender, AppointmentState}
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import helpers.StringUtils._
import javax.inject.Inject
import org.quartz._
import play.api.Configuration
import play.api.libs.json.{JsNull, JsValue, Json}
import services.{AppointmentService, AuditLogContext, OwnerService, UserPreferencesService}
import warwick.office365.{O365, O365Service}
import warwick.sso.Usercode

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
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
  userPreferencesService: UserPreferencesService,
  config: Configuration
)(implicit ec: ExecutionContext) extends AbstractJob(scheduler) {

  private[this] lazy val domain: String = config.get[String]("domain")
  private[this] lazy val office365Domain: String = config.get[String]("office365.domain")

  object OwnerAndOutlookId {
    def fromMap(input: Map[String, String]): Set[OwnerAndOutlookId] =
      input.map { case (usercode, outlookId) => OwnerAndOutlookId(Usercode(usercode), outlookId.maybeText) }.toSet
  }
  case class OwnerAndOutlookId(owner: Usercode, outlookId: Option[String])

  override val doLog: Boolean = false

  // Doesn't really matter, not used if doLog = false
  override def getDescription(context: JobExecutionContext): String = s"Update Appointments in Office365 for ${context.getMergedJobDataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId)}"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    val dataMap = context.getMergedJobDataMap
    val appointmentId = UUID.fromString(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId))
    val owners = OwnerAndOutlookId.fromMap(Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners)).as[Map[String, String]])
    val retries = Try(dataMap.getIntegerFromString(UpdateAppointmentInOffice365Job.JobDataMapKeys.retries)).map(_.intValue()).getOrElse(0)

    appointmentService.findFull(appointmentId).flatMap(_.fold(
      errors => errors.head.cause.map(t => throw t).getOrElse(throw new RuntimeException(errors.head.message)),
      render => {
        if (render.appointment.state == AppointmentState.Cancelled) {
          Future.sequence(owners.toSeq.filter(_.outlookId.nonEmpty).map(remove))
        } else {
          userPreferencesService.get(owners.map(_.owner)).flatMap(_.fold(
            errors => errors.head.cause.map(t => throw t).getOrElse(throw new RuntimeException(errors.head.message)),
            preferences => {
              val (currentOwners, noLongerOwners) = owners.partition(o => render.teamMembers.map(_.member.usercode).contains(o.owner))
              val removals = noLongerOwners.filter(_.outlookId.nonEmpty).toSeq.map(remove)

              val (toSave, toUpdate) = currentOwners.partition(_.outlookId.isEmpty)

              val saves = toSave
                .filter(o => preferences(o.owner).office365Enabled)
                .toSeq.map(o => save(render, o))

              val updates = toUpdate
                .filter(o => preferences(o.owner).office365Enabled)
                .toSeq.map(o => update(render, o))

              Future.sequence(removals ++ saves ++ updates)
            }
          ))
        }
      }
    )).map { results =>
      val (successful, failed) = results.partition(_.isRight)

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
          logger.error(s"$message - retrying failed in 1 minute")
          rescheduleFor(scheduler, context)(1.minute, trigger =>
            trigger
              .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners, Json.stringify(Json.toJson(failedOwners.map(o => o.owner.string -> o.outlookId.getOrElse("")).toMap)))
              .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.retries, (retries + 1).toString)
          )
        } else {
          logger.error(s"$message - max retries reached")
        }
      }

      JobResult.quiet
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
      "DateTime" -> O365.office365DateFormat.format(a.appointment.start),
      "TimeZone" -> "Europe/London"
    ),
    "End" -> Json.obj(
      "DateTime" -> O365.office365DateFormat.format(a.appointment.start.plusMinutes(a.appointment.duration.toMinutes)),
      "TimeZone" -> "Europe/London"
    ),
    "WebLink" -> s"https://$domain${controllers.admin.routes.AppointmentController.view(a.appointment.key).url}",
    "Location" -> Json.obj(
      "DisplayName" -> a.room.map(r => s"${r.name}, ${r.building.name}").getOrElse[String](""),
      "Address" -> JsNull
    ),
    "Attendees" -> (
      if (a.room.exists(_.o365Usercode.nonEmpty))
        Json.arr(
          a.room.flatMap { room => room.o365Usercode.map { usercode =>
            Json.obj(
              "EmailAddress" -> Json.obj(
                "Address" -> s"${usercode.string}@$office365Domain",
                "Name" -> s"${room.name}, ${room.building.name}"
              ),
              "Type" -> "Resource"
            )
          }}
        )
      else Json.arr()
    )
  )
}
