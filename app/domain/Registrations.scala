package domain

import java.time.ZonedDateTime

import play.api.libs.json.{Format, Json}
import warwick.sso.UniversityID

object Registrations {

  object StudentSupportData {
    import Disability._
    import Medication._
    import RegistrationReferral._
    implicit val formatter: Format[StudentSupportData] = Json.format[StudentSupportData]
  }

  case class StudentSupportData(
    summary: String,
    gp: String,
    tutor: String,
    disabilities: Set[Disability],
    medications: Set[Medication],
    appointmentAdjustments: String,
    referrals: Set[RegistrationReferral]
  )

  case class StudentSupport(
    universityID: UniversityID,
    updatedDate: ZonedDateTime,
    data: StudentSupportData
  )

}
