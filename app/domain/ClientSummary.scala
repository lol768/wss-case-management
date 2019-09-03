package domain

import java.time.{Instant, OffsetDateTime}

import domain.ClientRiskStatus.{High, Medium}
import domain.History._
import domain.dao.ClientSummaryDao.{StoredClientSummary, StoredClientSummaryVersion}
import enumeratum.{EnumEntry, PlayEnum}
import play.api.libs.json.{Format, Json, OFormat, Writes}
import warwick.core.helpers.JavaTime
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.sso.{User, UserLookupService, Usercode}

import scala.collection.immutable
import scala.concurrent.Future

case class ClientSummary(
  client: Client,
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment],
  reasonableAdjustmentsNotes: String,
  initialConsultation: Option[InitialConsultation],
  updatedDate: OffsetDateTime
) {
  def toSave: ClientSummarySave = ClientSummarySave(
    notes = notes,
    alternativeContactNumber = alternativeContactNumber,
    alternativeEmailAddress = alternativeEmailAddress,
    riskStatus = riskStatus,
    reasonableAdjustments = reasonableAdjustments,
    reasonableAdjustmentsNotes = reasonableAdjustmentsNotes,
  )
}

case class ClientSummarySave(
  notes: String,
  alternativeContactNumber: String,
  alternativeEmailAddress: String,
  riskStatus: Option[ClientRiskStatus],
  reasonableAdjustments: Set[ReasonableAdjustment],
  reasonableAdjustmentsNotes: String,
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
  lastUpdatedCase: Option[OffsetDateTime],
  lastUpdatedEnquiry: Option[OffsetDateTime],
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

case class InitialConsultation(
  reason: String,
  suggestedResolution: String,
  alreadyTried: String,
  sessionFeedback: String,
  administratorOutcomes: String,
  createdDate: OffsetDateTime,
  updatedDate: OffsetDateTime,
  updatedBy: Usercode
) {
  lazy val nonEmpty: Boolean = reason.nonEmpty || suggestedResolution.nonEmpty || alreadyTried.nonEmpty || sessionFeedback.nonEmpty || administratorOutcomes.nonEmpty
  lazy val isEmpty: Boolean = !nonEmpty
}

object InitialConsultation {
  implicit val usercodeFormat: Format[Usercode] = Json.format[Usercode]
  implicit val formatter: OFormat[InitialConsultation] = Json.format[InitialConsultation]
}

case class InitialConsultationSave(
  reason: String,
  suggestedResolution: String,
  alreadyTried: String,
  sessionFeedback: String,
  administratorOutcomes: String,
  version: Option[OffsetDateTime],
)

object InitialConsultationSave {
  implicit val formatter: OFormat[InitialConsultationSave] = Json.format[InitialConsultationSave]
}
