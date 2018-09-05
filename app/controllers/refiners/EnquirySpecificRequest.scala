package controllers.refiners

import domain.{Enquiry, MessageData}
import warwick.sso.AuthenticatedRequest

class EnquirySpecificRequest[A](val enquiry: Enquiry, val messages: Seq[MessageData], request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
