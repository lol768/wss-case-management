package controllers.refiners

import domain.dao.CaseDao.Case
import warwick.sso.AuthenticatedRequest

class CaseSpecificRequest[A](val `case`: Case, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
