package controllers.refiners

import domain.CaseNote
import warwick.sso.AuthenticatedRequest

class CaseNoteSpecificRequest[A](val caseNote: CaseNote, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
