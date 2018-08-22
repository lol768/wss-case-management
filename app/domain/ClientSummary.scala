package domain

import java.time.OffsetDateTime

import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.libs.json.{Format, Json}
import warwick.sso.UniversityID

import scala.collection.immutable

case class ClientSummary(
  universityID: UniversityID,
  data: ClientSummaryData,
  updatedDate: OffsetDateTime = JavaTime.offsetDateTime,
)

case class ClientSummaryData(
  highMentalHealthRisk: Option[Boolean],
  fields: ClientSummaryFields
)

case class ClientSummaryFields(
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment]
)

object ClientSummaryFields {
  implicit val formatter: Format[ClientSummaryFields] = Json.format[ClientSummaryFields]
}

sealed trait ClientRiskStatus extends EnumEntry
object ClientRiskStatus extends PlayEnum[ClientRiskStatus] {
  case object Low extends ClientRiskStatus
  case object Medium extends ClientRiskStatus
  case object High extends ClientRiskStatus

  val values: immutable.IndexedSeq[ClientRiskStatus] = findValues
}