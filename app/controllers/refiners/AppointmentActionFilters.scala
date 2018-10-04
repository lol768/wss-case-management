package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{AppointmentService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class AppointmentActionFilters @Inject()(
  appointmentService: AppointmentService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  implicit val implicitAppointmentService: AppointmentService = appointmentService

  private val CanViewAppointment = PermissionsFilter[AppointmentSpecificRequest] { implicit request =>
    permissionService.canViewAppointment(request.context.user.get.usercode)
  }

  private val CanEditAppointment = PermissionsFilter[AppointmentSpecificRequest] { implicit request =>
    permissionService.canEditAppointment(request.context.user.get.usercode, request.appointment.id)
  }

  private val CanClientManageAppointment = PermissionsFilter[AppointmentSpecificRequest] { implicit request =>
    permissionService.canClientManageAppointment(request.context.user.get, request.appointment.id)
  }

  def CanViewAppointmentAction(appointmentKey: IssueKey): ActionBuilder[AppointmentSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithAppointment(appointmentKey) andThen CanViewAppointment

  def CanEditAppointmentAction(appointmentKey: IssueKey): ActionBuilder[AppointmentSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithAppointment(appointmentKey) andThen CanEditAppointment

  def CanClientManageAppointmentAction(appointmentKey: IssueKey): ActionBuilder[AppointmentSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithAppointment(appointmentKey) andThen CanClientManageAppointment

}
