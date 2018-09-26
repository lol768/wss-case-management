package domain

import java.time.OffsetDateTime

import enumeratum.{EnumEntry, PlayEnum}
import play.api.libs.json.{Format, Json}
import warwick.sso.UniversityID

import scala.collection.immutable

case class ClientSummary(
  universityID: UniversityID,
  highMentalHealthRisk: Option[Boolean],
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment],
  updatedDate: OffsetDateTime
) {
  def toSave = ClientSummarySave(
    highMentalHealthRisk = highMentalHealthRisk,
    notes = notes,
    alternativeContactNumber = alternativeContactNumber,
    alternativeEmailAddress = alternativeEmailAddress,
    riskStatus = riskStatus,
    reasonableAdjustments = reasonableAdjustments
  )
}

case class ClientSummarySave(
  highMentalHealthRisk: Option[Boolean],
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment]
)

object ClientSummarySave {
  val formatter: Format[ClientSummarySave] = Json.format[ClientSummarySave]
}

sealed trait ClientRiskStatus extends EnumEntry
object ClientRiskStatus extends PlayEnum[ClientRiskStatus] {
  case object Low extends ClientRiskStatus
  case object Medium extends ClientRiskStatus
  case object High extends ClientRiskStatus

  val values: immutable.IndexedSeq[ClientRiskStatus] = findValues
}