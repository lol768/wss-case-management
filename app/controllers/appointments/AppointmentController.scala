package controllers.appointments

import controllers.BaseController
import controllers.refiners.AppointmentActionFilters
import domain.{AppointmentCancellationReason, AppointmentState, IssueKey}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.AppointmentService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppointmentController @Inject()(
  appointments: AppointmentService,
  appointmentActionFilters: AppointmentActionFilters,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import appointmentActionFilters._

  private def redirectBack(): Result =
    Redirect(controllers.routes.IndexController.home().withFragment("myappointments"))

  def accept(appointmentKey: IssueKey): Action[AnyContent] = CanClientManageAppointmentAction(appointmentKey).async { implicit request =>
    val universityID = currentUser().universityId.get

    appointments.getClients(request.appointment.id).successFlatMap { clients =>
      val client = clients.find(_.universityID == universityID).get
      if (client.state != AppointmentState.Provisional) {
        // Can only accept appointments that are provisional for you
        Future.successful(redirectBack())
      } else appointments.clientAccept(request.appointment.id, universityID).successMap { appointment =>
        if (appointment.state == AppointmentState.Confirmed)
          redirectBack().flashing("success" -> Messages("flash.appointment.confirmed"))
        else
          redirectBack().flashing("success" -> Messages("flash.appointment.accepted"))
      }
    }
  }

  def decline(appointmentKey: IssueKey): Action[AnyContent] = CanClientManageAppointmentAction(appointmentKey).async { implicit request =>
    val universityID = currentUser().universityId.get

    Form(single("cancellationReason" -> AppointmentCancellationReason.formField)).bindFromRequest().fold(
      _ => Future.successful(redirectBack()), // Ignore
      cancellationReason => appointments.getClients(request.appointment.id).successFlatMap { clients =>
        val client = clients.find(_.universityID == universityID).get
        if (client.state == AppointmentState.Cancelled && client.cancellationReason.contains(cancellationReason)) {
          // Trying to decline an appointment with a no-op
          Future.successful(redirectBack())
        } else appointments.clientDecline(request.appointment.id, universityID, cancellationReason).successMap { appointment =>
          redirectBack().flashing("success" -> Messages("flash.appointment.declined"))
        }
      }
    )
  }

}