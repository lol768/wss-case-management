package controllers.refiners

import domain.CaseNote
import domain.dao.CaseDao
import domain.dao.CaseDao.NoteAndCase
import warwick.sso.AuthenticatedRequest

class CaseNoteSpecificRequest[A](val noteAndCase: NoteAndCase, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
  val note: CaseNote = noteAndCase.note
  val `case`: CaseDao.Case = noteAndCase.clientCase
}
