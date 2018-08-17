package domain

import java.time.OffsetDateTime

import play.api.libs.json.{Format, Json}
import warwick.sso.UniversityID

case class Registration(
  universityID: UniversityID,
  updatedDate: OffsetDateTime,
  data: RegistrationData
)

object RegistrationData {
  import Disability._
  import Medication._
  import RegistrationReferral._
  implicit val formatter: Format[RegistrationData] = Json.format[RegistrationData]
}

case class RegistrationData(
  gp: String,
  tutor: String,
  disabilities: Set[Disability],
  medications: Set[Medication],
  appointmentAdjustments: String,
  referrals: Set[RegistrationReferral]
)