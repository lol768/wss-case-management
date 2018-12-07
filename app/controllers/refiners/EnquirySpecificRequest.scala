package controllers.refiners

import java.time.OffsetDateTime

import domain.Enquiry
import warwick.sso.AuthenticatedRequest

class EnquirySpecificRequest[A](val enquiry: Enquiry, val lastEnquiryMessageDate: OffsetDateTime, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
