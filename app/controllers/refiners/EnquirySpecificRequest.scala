package controllers.refiners

import domain.Enquiry
import warwick.sso.AuthenticatedRequest

class EnquirySpecificRequest[A](val enquiry: Enquiry, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
