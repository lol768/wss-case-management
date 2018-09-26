package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{AppointmentService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanViewAppointmentActionRefiner @Inject()(
  appointmentService: AppointmentService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  implicit val implicitAppointmentService: AppointmentService = appointmentService

  private val CanViewAppointment = PermissionsFilter[AppointmentSpecificRequest] { implicit request =>
    permissionService.canViewAppointment(request.context.user.get.usercode)
  }

  def CanViewAppointmentAction(appointmentKey: IssueKey): ActionBuilder[AppointmentSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithAppointment(appointmentKey) andThen CanViewAppointment

}
