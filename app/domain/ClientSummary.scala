package domain

import java.time.{Instant, OffsetDateTime}

import domain.ClientRiskStatus.{High, Medium}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.libs.json.{Format, Json}

import scala.collection.immutable

case class ClientSummary(
  client: Client,
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

case class AtRiskClient(
  summary: ClientSummary,
  lastUpdatedEnquiry: Option[OffsetDateTime],
  lastUpdatedCase: Option[OffsetDateTime]
) extends Ordered[AtRiskClient] {
  override def compare(that: AtRiskClient): Int = {
    if (this.summary.highMentalHealthRisk.contains(true) && !that.summary.highMentalHealthRisk.contains(true)) {
      -1
    } else if (!this.summary.highMentalHealthRisk.contains(true) && that.summary.highMentalHealthRisk.contains(true)) {
      1
    } else if (this.summary.riskStatus.contains(High) && !that.summary.riskStatus.contains(High)) {
      -1      
    } else if (!this.summary.riskStatus.contains(High) && that.summary.riskStatus.contains(High)) {
      1
    } else if (this.summary.riskStatus.contains(Medium) && !that.summary.riskStatus.contains(Medium)) {
      -1
    } else if (!this.summary.riskStatus.contains(Medium) && that.summary.riskStatus.contains(Medium)) {
      1
    } else {
      val thePast = OffsetDateTime.ofInstant(Instant.ofEpochMilli(0), JavaTime.timeZone)
      JavaTime.dateTimeOrdering.compare(
        // Reverse this and that = newest first
        Seq(that.lastUpdatedCase.getOrElse(thePast), that.lastUpdatedEnquiry.getOrElse(thePast)).max(JavaTime.dateTimeOrdering),
        Seq(this.lastUpdatedCase.getOrElse(thePast), this.lastUpdatedEnquiry.getOrElse(thePast)).max(JavaTime.dateTimeOrdering)
      )
    }
  }
}