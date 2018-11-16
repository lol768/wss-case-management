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
    attendanceState: Option[AppointmentClientAttendanceState],
    cancellationReason: Option[AppointmentCancellationReason],
  )

  case class AppointmentOutcomesFormData(
    attendance: Seq[AppointmentClientAttendanceFormData],
    outcomes: AppointmentOutcomesSave,
    note: Option[String],
    version: OffsetDateTime,
  )

  def form(a: AppointmentRender): Form[AppointmentOutcomesFormData] =
    Form(
      mapping(
        "attendance" -> seq(
          mapping(
            "client" -> nonEmptyText.transform[UniversityID](UniversityID.apply, _.string)
              .verifying("error.client.invalid", u => a.clients.map(_.client.universityID).contains(u)),
            "attendanceState" -> optional(AppointmentClientAttendanceState.formField).verifying("error.required", _.nonEmpty),
            "cancellationReason" -> optional(AppointmentCancellationReason.formField),
          )(AppointmentClientAttendanceFormData.apply)(AppointmentClientAttendanceFormData.unapply)
        ),
        "outcomes" -> mapping(
          "outcome" -> set(AppointmentOutcome.formField),
          "dsaSupportAccessed" -> optional(AppointmentDSASupportAccessed.formField).verifying("error.appointment.dsaSupportAccessed.invalid", _.forall(AppointmentDSASupportAccessed.valuesFor(a.appointment.team).contains)),
          "dsaActionPoints" -> AppointmentDSAActionPoint.formMapping,
        )(AppointmentOutcomesSave.apply)(AppointmentOutcomesSave.unapply),
        "note" -> optional(text),
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
            AppointmentClientAttendanceFormData(
              client.client.universityID,
              client.attendanceState,
              client.cancellationReason
            )
          },
          AppointmentOutcomesSave(
            a.appointment.outcome,
            a.appointment.dsaSupportAccessed,
            a.appointment.dsaActionPoints,
          ),
          None,
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
          data.attendance.filter(_.attendanceState.nonEmpty).map { d => (d.client, (d.attendanceState.get, d.cancellationReason)) }.toMap,
          data.outcomes,
          data.note.map(CaseNoteSave(_, currentUser().usercode)),
          data.version
        ).successMap { updated =>
          Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
            .flashing("success" -> Messages("flash.appointment.updated"))
        }
      )
    }
  }

}
