package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class CounsellingServicesIssue(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object CounsellingServicesIssue extends PlayEnum[CounsellingServicesIssue] {
  case object Abuse extends CounsellingServicesIssue("A - Abuse")
  case object Academic extends CounsellingServicesIssue("B - Academic")
  case object Anxiety extends CounsellingServicesIssue("C - Anxiety")
  case object AddictiveBehaviours extends CounsellingServicesIssue("D - Addictive behaviours")
  case object Depressive extends CounsellingServicesIssue("E - Depressive/Mood disorder")
  case object Loss extends CounsellingServicesIssue("H - Loss")
  case object Other extends CounsellingServicesIssue("J - Other mental health conditions")
  case object PhysicalHealth extends CounsellingServicesIssue("K - Physical health")
  case object EatingDisorder extends CounsellingServicesIssue("L - Eating disorder")
  case object Relationships extends CounsellingServicesIssue("M - Relationships")
  case object SelfAndIdentity extends CounsellingServicesIssue("R - Self and identity")
  case object SexualIssues extends CounsellingServicesIssue("S - Sexual issues")
  case object Transitions extends CounsellingServicesIssue("T - Transitions")
  case object WelfareAndEmployment extends CounsellingServicesIssue("U - Welfare and employment")
  case object SelfHarm extends CounsellingServicesIssue("X - Self-harm")

  override def values: immutable.IndexedSeq[CounsellingServicesIssue] = findValues
}
