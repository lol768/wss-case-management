package controllers.refiners

import domain.{Appointment, AppointmentNote}
import warwick.sso.AuthenticatedRequest

class AppointmentNoteSpecificRequest[A](val note: AppointmentNote, val appointment: Appointment, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
