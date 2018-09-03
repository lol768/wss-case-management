package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed trait IssueState extends EnumEntry

object IssueState extends PlayEnum[IssueState] {
  case object Open extends IssueState
  case object Closed extends IssueState
  case object Reopened extends IssueState
  val values: immutable.IndexedSeq[IssueState] = findValues
}
