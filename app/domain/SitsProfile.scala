package domain

import java.time.LocalDate

import enumeratum.EnumEntry.CapitalWords
import enumeratum.{Enum, EnumEntry}
import helpers.StringUtils._
import warwick.core.helpers.JavaTime
import warwick.sso._

import scala.collection.immutable

case class SitsProfile(
  universityID: UniversityID,
  usercode: Usercode,
  fullName: String,
  dateOfBirth: LocalDate,
  phoneNumber: Option[String],
  warwickEmail: Option[String], // Only used for notification sending, not displayed on profile
  address: Option[Address],
  department: SitsDepartment,
  course: Option[Course],
  attendance: Option[Attendance],
  group: Option[StudentGroup],
  yearOfStudy: Option[YearOfStudy],
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  nationality: Option[String],
  dualNationality: Option[String],
  tier4VisaRequired: Option[Boolean],
  disability: Option[SitsDisability],
  disabilityFundingStatus: Option[SitsDisabilityFundingStatus],
  jobTitle: Option[String],
  photo: Option[String],
  userType: UserType
) {
  def asUser: User = User(
    usercode = usercode,
    universityId = Some(universityID),
    name = Name(fullName.split(' ').headOption, fullName.split(' ').lastOption),
    email = warwickEmail,
    department = Some(Department(None, Some(department.name), Some(department.code.toUpperCase))),
    userSource = Some("Tabula"),
    isStaffOrPGR = group.isEmpty || group.contains(StudentGroup.PGR),
    isStaffNotPGR = group.isEmpty,
    isStudent = userType == UserType.Student,
    isAlumni = false,
    isUndergraduate = group.contains(StudentGroup.Undergraduate) || group.contains(StudentGroup.Foundation),
    isPGT = group.contains(StudentGroup.PGT),
    isPGR = group.contains(StudentGroup.PGR),
    isFound = true,
    isVerified = true,
    isLoginDisabled = endDate.exists(_.isAfter(JavaTime.localDate)),
    rawProperties = Map()
  )
}

object SitsProfile {
  def universityId(e: Either[UniversityID, SitsProfile]): UniversityID = e.fold(identity, _.universityID)
}

case class Course (
  code: String,
  name: String
)

case class SitsDepartment(
  code: String,
  name: String
)

sealed abstract class UserType extends EnumEntry with CapitalWords

object UserType extends Enum[UserType] {
  val values: immutable.IndexedSeq[UserType] = findValues

  case object Other extends UserType
  case object Student extends UserType
  case object Staff extends UserType
  case object EmeritusAcademic extends UserType
  case object Applicant extends UserType

  def apply(user: User): UserType =
    if (user.isStudent || user.isPGR) UserType.Student
    else if (user.isStaffNotPGR) UserType.Staff
    // TODO else if (user.isApplicant) UserType.Applicant
    else UserType.Other
}

sealed abstract class Attendance(override val entryName: String, val description: String) extends EnumEntry {
  // Default constructor for serialization
  def this() = this("", "")
}

object Attendance extends Enum[Attendance] {
  val values: immutable.IndexedSeq[Attendance] = findValues

  case object FullTime extends Attendance("F", "Full-time")
  case object PartTime extends Attendance("P", "Part-time")
}

sealed abstract class StudentGroup(override val entryName: String, val description: String) extends EnumEntry {
  // Default constructor for serialization
  def this() = this("", "")
}

object StudentGroup extends Enum[StudentGroup] {

  val values: immutable.IndexedSeq[StudentGroup] = findValues

  case object Foundation extends StudentGroup("F", "Foundation course")
  case object Undergraduate extends StudentGroup("UG", "Undergraduate")
  case object PGT extends StudentGroup("PG(T)", "Postgraduate (taught)")
  case object PGR extends StudentGroup("PG(R)", "Postgraduate (research)")
}

case class YearOfStudy(
  block: Int,
  level: String
)

case class SitsDisability(
  code: String,
  description: String,
  sitsDefinition: String,
)

case class SitsDisabilityFundingStatus(
  code: String,
  description: String,
)

case class Address (
  line1: Option[String],
  line2: Option[String],
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postcode: Option[String],
) {
  override def toString: String = Seq(line1, line2, line3, line4, line5, postcode)
    .flatten.filter(_.hasText).mkString(", ")
}

case class VisaStatus (
  tier4: Boolean,
  requiresClearance: Boolean
)
