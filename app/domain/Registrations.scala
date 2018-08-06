package domain

import java.time.ZonedDateTime

import play.api.libs.json.{Format, Json}
import warwick.sso.UniversityID

object Registrations {

  object CounsellingData {
    import Disability._
    import Medication._
    import RegistrationReferral._
    import AppointmentAvailability._
    import CounsellingType._
    import PreviousCounselling._
    implicit val formatter: Format[CounsellingData] = Json.format[CounsellingData]
  }

  case class CounsellingData(
    counsellingTypes: Set[CounsellingType],
    gp: String,
    tutor: String,
    disabilities: Set[Disability],
    medications: Set[Medication],
    previousCounselling: Set[PreviousCounselling],
    appointmentAvailability: AppointmentAvailability,
    appointmentAdjustments: String,
    referrals: Set[RegistrationReferral]
  )

  case class Counselling(
    universityID: UniversityID,
    updatedDate: ZonedDateTime,
    data: CounsellingData
  )

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
