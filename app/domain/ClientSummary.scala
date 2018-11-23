package domain

import java.time.{Instant, OffsetDateTime}

import domain.History._
import domain.ClientRiskStatus.{High, Medium}
import domain.dao.ClientSummaryDao.{StoredClientSummary, StoredClientSummaryVersion}
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import play.api.libs.json.{Format, Json, Writes}
import services.{AuditLogContext, ClientService}
import warwick.core.helpers.JavaTime
import warwick.sso.{User, UserLookupService, Usercode}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

case class ClientSummary(
  client: Client,
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment],
  updatedDate: OffsetDateTime
) {
  def toSave = ClientSummarySave(
    notes = notes,
    alternativeContactNumber = alternativeContactNumber,
    alternativeEmailAddress = alternativeEmailAddress,
    riskStatus = riskStatus,
    reasonableAdjustments = reasonableAdjustments
  )
}

case class ClientSummarySave(
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
    if (this.summary.riskStatus.contains(High) && !that.summary.riskStatus.contains(High)) {
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

object ClientSummaryHistory {

  val writer: Writes[ClientSummaryHistory] = (r: ClientSummaryHistory) =>
    Json.obj(
      "riskStatus" -> toJson(r.riskStatus)
    )

  def apply(
    history: Seq[StoredClientSummaryVersion],
    userLookupService: UserLookupService
  ): Future[ServiceResult[ClientSummaryHistory]] = {
    val usercodes = history.flatMap(_.auditUser)
    implicit val usersByUsercode: Map[Usercode, User] = userLookupService.getUsers(usercodes.distinct).toOption.getOrElse(Map())

    def typedSimpleFieldHistory[A](f: StoredClientSummaryVersion => A) = simpleFieldHistory[StoredClientSummary, StoredClientSummaryVersion, A](history, f)

    Future.successful(Right(
      ClientSummaryHistory(
        riskStatus = typedSimpleFieldHistory(_.riskStatus),
      )
    ))
  }

}

case class ClientSummaryHistory(
  riskStatus: FieldHistory[Option[ClientRiskStatus]]
)