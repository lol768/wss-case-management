package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json, OFormat}
import warwick.sso.{UniversityID, Usercode}

case class ClientConsultation(
  id: UUID,
  universityID: UniversityID,
  reason: String,
  suggestedResolution: String,
  alreadyTried: String,
  sessionFeedback: String,
  administratorOutcomes: String,
  createdDate: OffsetDateTime,
  updatedDate: OffsetDateTime,
  updatedBy: Usercode
)

object ClientConsultation {
  def tupled = (apply _).tupled

  implicit val universityIDFormat: Format[UniversityID] = Json.format[UniversityID]
  implicit val usercodeFormat: Format[Usercode] = Json.format[Usercode]
  implicit val formatter: OFormat[ClientConsultation] = Json.format[ClientConsultation]
}

case class ClientConsultationSave(
  reason: String,
  suggestedResolution: String,
  alreadyTried: String,
  sessionFeedback: String,
  administratorOutcomes: String,
  version: Option[OffsetDateTime],
)

object ClientConsultationSave {
  implicit val formatter: OFormat[ClientConsultationSave] = Json.format[ClientConsultationSave]
}

