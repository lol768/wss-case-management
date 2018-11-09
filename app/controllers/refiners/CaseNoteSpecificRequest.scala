package controllers.refiners

import domain.{Case, CaseNote, NoteAndCase}
import warwick.sso.AuthenticatedRequest

class CaseNoteSpecificRequest[A](val noteAndCase: NoteAndCase, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
  val note: CaseNote = noteAndCase.note
  val `case`: Case = noteAndCase.clientCase
}
