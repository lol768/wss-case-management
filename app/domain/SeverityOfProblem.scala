package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class SeverityOfProblem(val description: String) extends EnumEntry
object SeverityOfProblem extends PlayEnum[SeverityOfProblem] {
  case object VeryMild extends SeverityOfProblem("0 - Very mild")
  case object Mild extends SeverityOfProblem("1 - Mild")
  case object Moderate extends SeverityOfProblem("2 - Moderate")
  case object ModeratelySevere extends SeverityOfProblem("3 - Moderately severe")
  case object Severe extends SeverityOfProblem("4 - Severe")
  case object VerySevere extends SeverityOfProblem("5 - Very severe")
  case object ExtremelySevere extends SeverityOfProblem("6 - Extremely severe")
  case object Incapacitating extends SeverityOfProblem("7 - Incapacitating")

  override def values: immutable.IndexedSeq[SeverityOfProblem] = findValues
}
