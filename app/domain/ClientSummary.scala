package domain

import java.time.OffsetDateTime

import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.libs.json.{Format, JsValue, Json}
import warwick.sso.UniversityID

import scala.collection.immutable

case class ClientSummary(
  universityID: UniversityID,
  data: ClientSummaryData,
  updatedDate: OffsetDateTime = JavaTime.offsetDateTime,
)

object ClientSummaryData {
  val formatter: Format[ClientSummaryData] = Json.format[ClientSummaryData]
}

case class ClientSummaryData(
  highMentalHealthRisk: Option[Boolean],
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment]
) {
  def getJsonFields: JsValue = Json.obj(
    "notes" -> notes,
    "alternativeContactNumber" -> alternativeContactNumber,
    "alternativeEmailAddress" -> alternativeEmailAddress,
    "riskStatus" -> riskStatus,
    "reasonableAdjustments" -> reasonableAdjustments
  )
}

sealed trait ClientRiskStatus extends EnumEntry
object ClientRiskStatus extends PlayEnum[ClientRiskStatus] {
  case object Low extends ClientRiskStatus
  case object Medium extends ClientRiskStatus
  case object High extends ClientRiskStatus

  val values: immutable.IndexedSeq[ClientRiskStatus] = findValues
}