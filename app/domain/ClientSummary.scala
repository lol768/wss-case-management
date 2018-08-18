package domain

import java.time.OffsetDateTime

import enumeratum.EnumEntry.CapitalWords
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
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: ClientRiskStatus,
  reasonableAdjustments: Set[ReasonableAdjustment],
  alertFlags: Set[AlertFlag]
)

object ClientSummaryData {
  implicit val formatter: Format[ClientSummaryData] = Json.format[ClientSummaryData]
}

sealed trait ClientRiskStatus extends EnumEntry
object ClientRiskStatus extends PlayEnum[ClientRiskStatus] {
  case object Low extends ClientRiskStatus
  case object Medium extends ClientRiskStatus
  case object High extends ClientRiskStatus

  val values: immutable.IndexedSeq[ClientRiskStatus] = findValues
}

sealed trait AlertFlag extends EnumEntry
object AlertFlag extends PlayEnum[AlertFlag] with CapitalWords {
  case object HighMentalHealthRisk extends AlertFlag

  val values: immutable.IndexedSeq[AlertFlag] = findValues
}