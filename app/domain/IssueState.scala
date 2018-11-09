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

sealed trait IssueStateFilter extends EnumEntry
object IssueStateFilter extends PlayEnum[IssueStateFilter] {
  case object Open extends IssueStateFilter
  case object Closed extends IssueStateFilter
  case object All extends IssueStateFilter

  val values: immutable.IndexedSeq[IssueStateFilter] = findValues
}