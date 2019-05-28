package controllers.sysadmin

import controllers.BaseController
import domain.AppointmentState
import domain.dao.AppointmentDao.AppointmentSearchQuery
import javax.inject.{Inject, Singleton}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.office365.Office365CalendarService
import services.{AppointmentService, SecurityService}
import warwick.core.helpers.JavaTime

import scala.concurrent.ExecutionContext

@Singleton
class PushToOutlookController @Inject()(
  securityService: SecurityService,
  appointmentService: AppointmentService,
  office365CalendarService: Office365CalendarService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  def form(): Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.pushToOutlook())
  }

  def submit(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    appointmentService.findForSearch(AppointmentSearchQuery(
      startAfter = Some(JavaTime.localDate),
      states = Set(AppointmentState.Provisional, AppointmentState.Accepted)
    )).successMap { appointments =>
      appointments.foreach(appointment =>
        office365CalendarService.updateAppointment(appointment.appointment.id, appointment.teamMembers)
      )
      Redirect(controllers.sysadmin.routes.PushToOutlookController.form())
        .flashing("success" -> Messages("flash.pushToOutlook.done"))
    }
  }

}
