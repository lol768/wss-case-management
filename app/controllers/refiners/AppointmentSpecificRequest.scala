package controllers.refiners

import domain.Appointment
import warwick.sso.AuthenticatedRequest

class AppointmentSpecificRequest[A](val appointment: Appointment, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
