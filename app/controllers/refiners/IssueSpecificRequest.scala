package controllers.refiners

import java.time.OffsetDateTime

import domain.Issue
import warwick.sso.AuthenticatedRequest

class IssueSpecificRequest[A](val issue: Issue, val lastMessageDate: Option[OffsetDateTime], request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
