package domain

import java.time.LocalDate

import enumeratum.EnumEntry.CapitalWords
import enumeratum.{Enum, EnumEntry}
import helpers.JavaTime
import helpers.StringUtils._
import warwick.sso.{Department, Name, UniversityID, User, Usercode}

import scala.collection.immutable

case class SitsProfile(
  universityID: UniversityID,
  usercode: Usercode,
  fullName: String,
  dateOfBirth: LocalDate,
  phoneNumber: Option[String],
  warwickEmail: Option[String],
  alternateEmail: Option[String],
  address: Option[Address],
  residence: Option[Residence],
  department: SitsDepartment,
  course: Option[Course],
  route: Option[Route],
  courseStatus: Option[CourseStatus],
  enrolmentStatus: Option[EnrolmentStatus],
  attendance: Option[Attendance],
  group: Option[StudentGroup],
  yearOfStudy: Option[YearOfStudy],
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  nationality: Option[String],
  dualNationality: Option[String],
  tier4VisaRequired: Option[Boolean],
  disability: Option[SitsDisability],
  photo: Option[String],
  personalTutors: Seq[SitsProfile],
  researchSupervisors: Seq[SitsProfile],
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

case class Route (
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

case class EnrolmentStatus (
  code: String,
  description: String
)

case class CourseStatus (
  code: String,
  description: String
)

case class SitsDisability (
  code: String,
  description: String,
  sitsDefinition: String,
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

sealed trait Residence extends EnumEntry with CapitalWords

object Residence extends Enum[Residence] {

  val values: immutable.IndexedSeq[Residence] = findValues

  case object ArthurVick extends Residence
  case object Benefactors extends Residence
  case object Bluebell extends Residence
  case object Claycroft extends Residence
  case object Cryfield extends Residence
  case object Heronbank extends Residence
  case object JackMartin extends Residence
  case object Lakeside extends Residence
  case object Redfern extends Residence
  case object Rootes extends Residence
  case object Sherbourne extends Residence
  case object Tocil extends Residence
  case object Westwood extends Residence
  case object Whitefields extends Residence
}
