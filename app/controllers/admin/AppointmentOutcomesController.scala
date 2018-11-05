package controllers.admin

import java.time.OffsetDateTime

import controllers.BaseController
import controllers.admin.AppointmentOutcomesController._
import controllers.refiners.AppointmentActionFilters
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.AppointmentService
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

object AppointmentOutcomesController {
  case class AppointmentClientAttendanceFormData(
    client: UniversityID,
    state: AppointmentState,
    cancellationReason: Option[AppointmentCancellationReason],
  )

  case class AppointmentOutcomesFormData(
    attendance: Seq[AppointmentClientAttendanceFormData],
    outcome: Option[AppointmentOutcome],
    version: OffsetDateTime,
  )

  def form(a: AppointmentRender): Form[AppointmentOutcomesFormData] =
    Form(
      mapping(
        "attendance" -> seq(
          mapping(
            "client" -> nonEmptyText.transform[UniversityID](UniversityID.apply, _.string)
              .verifying("error.client.invalid", u => a.clients.map(_.client.universityID).contains(u)),
            "state" -> AppointmentState.formField.verifying("error.required", s => s == AppointmentState.Attended || s == AppointmentState.Cancelled),
            "cancellationReason" -> optional(AppointmentCancellationReason.formField),
          )(AppointmentClientAttendanceFormData.apply)(AppointmentClientAttendanceFormData.unapply)
        ),
        "outcome" -> optional(AppointmentOutcome.formField).verifying("error.required", o => o.nonEmpty),
        "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == a.appointment.lastUpdated)
      )(AppointmentOutcomesFormData.apply)(AppointmentOutcomesFormData.unapply)
    )
}

@Singleton
class AppointmentOutcomesController @Inject()(
  appointments: AppointmentService,
  appointmentActionFilters: AppointmentActionFilters,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import appointmentActionFilters._

  def outcomesForm(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    appointments.findForRender(appointmentKey).successMap { a =>
      Ok(views.html.admin.appointments.outcomes(
        a,
        form(a).fill(AppointmentOutcomesFormData(
          a.clients.toSeq.sortBy(_.client.universityID.string).map { client =>
            AppointmentClientAttendanceFormData(client.client.universityID, client.state, client.cancellationReason)
          },
          a.appointment.outcome,
          a.appointment.lastUpdated
        ))
      ))
    }
  }

  def outcomes(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    appointments.findForRender(appointmentKey).successFlatMap { a =>
      form(a).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(views.html.admin.appointments.outcomes(
            a,
            formWithErrors
          ))
        ),
        data => appointments.recordOutcomes(
          a.appointment.id,
          data.attendance.map { d => (d.client, (d.state, d.cancellationReason)) }.toMap,
          data.outcome.get,
          data.version
        ).successMap { updated =>
          Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
            .flashing("success" -> Messages("flash.appointment.updated"))
        }
      )
    }
  }

}
