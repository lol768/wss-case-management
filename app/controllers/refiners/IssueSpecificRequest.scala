package controllers.refiners

import domain.Issue
import warwick.sso.AuthenticatedRequest

class IssueSpecificRequest[A](val issue: Issue, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
