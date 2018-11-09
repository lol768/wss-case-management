package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class ClientRiskType(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object ClientRiskType extends PlayEnum[ClientRiskType] {
  case object SelfHarm extends ClientRiskType("Self harm")
  case object SuicidalIdeation extends ClientRiskType("Suicidal ideation")
  case object SuicidalBehaviour extends ClientRiskType("Suicidal behaviour")
  case object SuicidalAttempts extends ClientRiskType("Suicidal attempts")
  case object SubstanceUse extends ClientRiskType("Substance use")
  case object DirectHarm extends ClientRiskType("Direct harm to others")
  case object IndirectHarm extends ClientRiskType("Indirect harm to others")
  case object OtherRisk extends ClientRiskType("Other risk")
  case object ProtectiveFactors extends ClientRiskType("Protective factors")
  case object Other extends ClientRiskType("Other")

  override def values: immutable.IndexedSeq[ClientRiskType] = findValues
}
