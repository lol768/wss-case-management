package controllers.refiners

import domain.Team
import warwick.sso.AuthenticatedRequest

class TeamSpecificRequest[A](val team: Team, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
