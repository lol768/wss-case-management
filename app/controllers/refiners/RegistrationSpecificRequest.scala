package controllers.refiners

import domain.Registration
import warwick.sso.AuthenticatedRequest

class RegistrationSpecificRequest[A](val registration: Registration, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
