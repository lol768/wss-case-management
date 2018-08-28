package services.tabula

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime}

import domain._
import helpers.JavaTime
import play.api.libs.functional.syntax._
import play.api.libs.json._
import warwick.sso.UniversityID

object TabulaResponseParsers {

  object TabulaProfileData {

    case class StudentCourseYearDetails(
      academicYear: String,
      yearOfStudy: Int,
      studyLevel: String,
      modeOfAttendance: String,
      enrolmentStatus: EnrolmentStatus,
      enrolmentDepartment: SitsDepartment
    )
    val studentCourseYearDetailsReads: Reads[StudentCourseYearDetails] = (
      (__ \ "academicYear").read[String] and
      (__ \ "yearOfStudy").read[Int] and
      (__ \ "studyLevel").read[String] and
      (__ \ "modeOfAttendance" \ "code").read[String] and
      (__ \ "enrolmentStatus").read[EnrolmentStatus](enrolmentStatusReads) and
      (__ \ "enrolmentDepartment").read[SitsDepartment](departmentReads)
    )(StudentCourseYearDetails.apply _)

    // A reduced Member object, defined again here so no cyclic dependency
    case class StudentRelationshipAgent(
      universityId: String,
      fullName: String,
      dateOfBirth: LocalDate,
      email: Option[String],
      homeDepartment: SitsDepartment,
      userType: String,
    )
    val studentRelationshipAgentReads: Reads[StudentRelationshipAgent] = (
      (__ \ "universityId").read[String] and
      (__ \ "fullName").read[String] and
      (__ \ "dateOfBirth").read[LocalDate] and
      (__ \ "email").readNullable[String] and
      (__ \ "homeDepartment").read[SitsDepartment](departmentReads) and
      (__ \ "userType").read[String]
    )(StudentRelationshipAgent.apply _)

    case class StudentRelationship(
      agent: StudentRelationshipAgent,
      startDate: OffsetDateTime,
      endDate: Option[OffsetDateTime],
      percentage: Option[BigDecimal]
    )
    val studentRelationshipReads: Reads[StudentRelationship] = (
      (__ \ "agent").read[StudentRelationshipAgent](studentRelationshipAgentReads) and
      (__ \ "startDate").read[OffsetDateTime] and
      (__ \ "endDate").readNullable[OffsetDateTime] and
      (__ \ "percentage").readNullable[BigDecimal]
    )(StudentRelationship.apply _)

    case class StudentCourseDetails(
      mostSignificant: Boolean,
      beginDate: LocalDate,
      endDate: LocalDate,
      courseType: String,
      course: Course,
      route: Route,
      courseStatus: CourseStatus,
      level: String,
      studentCourseYearDetails: Seq[StudentCourseYearDetails],
      relationships: Map[String, Seq[StudentRelationship]]
    )
    val studentCourseDetailsReads: Reads[StudentCourseDetails] = (
      (__ \ "mostSignificant").read[Boolean] and
      (__ \ "beginDate").read[LocalDate] and
      (__ \ "expectedEndDate").read[LocalDate] and
      (__ \ "course" \ "type").read[String] and
      (__ \ "course").read[Course](courseReads) and
      (__ \ "currentRoute").read[Route](routeReads) and
      (__ \ "statusOnCourse").read[CourseStatus](courseStatusReads) and
      (__ \ "levelCode").read[String] and
      (__ \ "studentCourseYearDetails").read[Seq[StudentCourseYearDetails]](Reads.seq(studentCourseYearDetailsReads)) and
      (__ \ "relationships").read[Map[String, Seq[StudentRelationship]]](Reads.map(Reads.seq(studentRelationshipReads)))
    )(StudentCourseDetails.apply _)

    case class Member(
      universityId: String,
      fullName: String,
      dateOfBirth: LocalDate,
      phoneNumber: Option[String],
      email: Option[String],
      homeEmail: Option[String],
      homeDepartment: SitsDepartment,
      tier4VisaRequirement: Option[Boolean],
      nationality: Option[String],
      secondNationality: Option[String],
      disability: Option[SitsDisability],
      residence: Option[String],
      address: Option[Address],
      studentCourseDetails: Option[Seq[StudentCourseDetails]],
      userType: String,
    ) {
      def toUserProfile: SitsProfile = {
        val latestScd = studentCourseDetails.flatMap(scds => scds.find(_.mostSignificant))
        val latestScyd = latestScd.flatMap(_.studentCourseYearDetails.lastOption)
        val department = latestScyd.map(_.enrolmentDepartment).getOrElse(homeDepartment)

        def currentAgents(relationshipType: String): Seq[SitsProfile] = {
          studentCourseDetails.toSeq
            .flatMap(_.flatMap(_.relationships.get(relationshipType).toSeq).flatten)
            .filter { relationship =>
              relationship.startDate.isBefore(JavaTime.offsetDateTime) &&
              !relationship.endDate.exists(_.isBefore(JavaTime.offsetDateTime))
            }
            .sortBy { rel => (rel.startDate, rel.agent.universityId) }
            .map(_.agent).distinct
            .map { agent =>
              SitsProfile(
                universityID = UniversityID(agent.universityId),
                fullName = agent.fullName,
                dateOfBirth = agent.dateOfBirth,
                phoneNumber = None,
                warwickEmail = agent.email,
                alternateEmail = None,
                address = None,
                residence = None,
                department = SitsDepartment(agent.homeDepartment.code, agent.homeDepartment.name),
                course = None,
                route = None,
                courseStatus = None,
                enrolmentStatus = None,
                attendance = None,
                group = None,
                yearOfStudy = None,
                startDate = None,
                endDate = None,
                nationality = None,
                dualNationality = None,
                tier4VisaRequired = None,
                disability = None,
                photo = None,
                personalTutors = Nil,
                researchSupervisors = Nil,
                userType = UserType.withName(agent.userType)
              )
            }
        }

        SitsProfile(
          universityID = UniversityID(universityId),
          fullName = fullName,
          dateOfBirth = dateOfBirth,
          phoneNumber = phoneNumber,
          warwickEmail = email,
          alternateEmail = homeEmail,
          address = address,
          residence = residence.flatMap(Residence.withNameOption),
          department = SitsDepartment(department.code, department.name),
          course = latestScd.map(_.course),
          route= latestScd.map(_.route),
          courseStatus = latestScd.map(_.courseStatus),
          enrolmentStatus = latestScyd.map(_.enrolmentStatus),
          attendance = latestScyd.map(_.modeOfAttendance).flatMap(Attendance.withNameOption),
          group = latestScd.map(_.courseType).flatMap(StudentGroup.withNameOption),
          yearOfStudy = latestScyd.map(scyd => YearOfStudy(scyd.yearOfStudy, scyd.studyLevel)),
          startDate = latestScd.map(_.beginDate),
          endDate = latestScd.map(_.endDate),
          nationality = nationality,
          dualNationality = secondNationality,
          tier4VisaRequired = tier4VisaRequirement,
          disability = disability,
          photo = None,
          personalTutors = currentAgents("tutor"),
          researchSupervisors = currentAgents("supervisor"),
          userType = UserType.withName(userType)
        )
      }
    }
    val memberReads: Reads[Member] = (
      (__ \ "member" \ "universityId").read[String] and
      (__ \ "member" \ "fullName").read[String] and
      (__ \ "member" \ "dateOfBirth").read[LocalDate] and
      (__ \ "member" \ "mobileNumber").readNullable[String] and
      (__ \ "member" \ "email").readNullable[String] and
      (__ \ "member" \ "homeEmail").readNullable[String] and
      (__ \ "member" \ "homeDepartment").read[SitsDepartment](departmentReads) and
      (__ \ "member" \ "tier4VisaRequirement").readNullable[Boolean] and
      (__ \ "member" \ "nationality").readNullable[String] and
      (__ \ "member" \ "secondNationality").readNullable[String] and
      (__ \ "member" \ "disability").readNullable[SitsDisability](disabilityReads) and
      (__ \ "member" \ "termtimeAddress").readNullable[Option[String]]((__ \ "line2").readNullable[String]).map(_.flatten) and
      (__ \ "member" \ "currentAddress").readNullable[Address](addressReads) and
      (__ \ "member" \ "studentCourseDetails").readNullable[Seq[StudentCourseDetails]](Reads.seq(studentCourseDetailsReads)) and
      (__ \ "member" \ "userType").read[String]
    )(Member.apply _)
  }

  private val codeAndNameBuilder = (__ \ "code").read[String] and (__ \ "name").read[String]

  val departmentReads: Reads[SitsDepartment] = codeAndNameBuilder(SitsDepartment.apply _)
  val courseReads: Reads[Course] = codeAndNameBuilder((code, name) => Course.apply(code, s"${code.toUpperCase} $name"))
  val routeReads: Reads[Route] = codeAndNameBuilder((code, name) => Route.apply(code, s"${code.toUpperCase} $name"))
  val enrolmentStatusReads: Reads[EnrolmentStatus] = codeAndNameBuilder(EnrolmentStatus.apply _)
  val courseStatusReads: Reads[CourseStatus] = codeAndNameBuilder(CourseStatus.apply _)

  val disabilityReads: Reads[SitsDisability] = (
    (__ \ "code").read[String] and
    (__ \ "definition").read[String] and
    (__ \ "sitsDefinition").read[String]
  )(SitsDisability.apply _)

  val addressReads: Reads[Address] = (
    (__ \ "line1").readNullable[String] and
    (__ \ "line2").readNullable[String] and
    (__ \ "line3").readNullable[String] and
    (__ \ "line4").readNullable[String] and
    (__ \ "line5").readNullable[String] and
    (__ \ "postcode").readNullable[String]
  )(Address.apply _)

  val universityIdResultReads: Reads[Seq[UniversityID]] = (__ \ "universityIds").read[Seq[String]].map(s => s.map(UniversityID))

  implicit val LocalDateReads: Reads[LocalDate] = new Reads[LocalDate] {

    final val DateFormat = "yyyy-MM-dd"

    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(s) => parseDate(s) match {
        case Some(d) => JsSuccess(d)
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.localdate.format", DateFormat))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
    }

    private def parseDate(input: String): Option[LocalDate] =
      scala.util.control.Exception.nonFatalCatch[LocalDate].opt(
        LocalDate.parse(input, DateTimeFormatter.ofPattern(DateFormat))
      )
  }

  case class MemberSearchResult(
    universityID: UniversityID,
    firstName: String,
    lastName: String,
    email: Option[String],
    alternateEmail: Option[String],
    department: SitsDepartment,
    userType: String,
    photo: Option[String]
  ) extends Ordered[MemberSearchResult] {
    // Sort applicants to the bottom, and new applicants before others
    override def compare(that: MemberSearchResult): Int = {
      if (this.userType != that.userType) {
        if (this.userType == "Applicant") {
          1
        } else if (that.userType == "Applicant") {
          -1
        } else {
          0
        }
      } else {
        if (this.userType == "Applicant") {
          that.universityID.string.compare(this.universityID.string)
        } else {
          0
        }
      }
    }
  }

  val memberSearchResultReads: Reads[MemberSearchResult] = (
    (__ \ "universityId").read[String].map[UniversityID](UniversityID.apply) and
    (__ \ "firstName").read[String] and
    (__ \ "lastName").read[String] and
    (__ \ "email").readNullable[String] and
    (__ \ "homeEmail").readNullable[String] and
    (__ \ "department").read[SitsDepartment](departmentReads) and
    (__ \ "userType").read[String] and
    Reads.pure(None)
    )(MemberSearchResult.apply _)

  val memberSearchResultsReads: Reads[Seq[MemberSearchResult]] = (__ \ "results").read[Seq[MemberSearchResult]](Reads.seq(memberSearchResultReads))

  private case class ErrorMessage(message: String)
  private val errorMessageReads = Json.reads[ErrorMessage]

  def validateAPIResponse[A](jsValue: JsValue, parser: Reads[A]): JsResult[A] = {
    (jsValue \ "success").validate[Boolean].flatMap {
      case false =>
        val status = (jsValue \ "status").validate[String].asOpt
        val errors = (jsValue \ "errors").validate[Seq[ErrorMessage]](Reads.seq(errorMessageReads)).asOpt
        JsError(__ \ "success", "Tabula API response not successful%s%s".format(
          status.map(s => s" (status: $s)").getOrElse(""),
          errors.map(e => s": ${e.map(_.message).mkString(", ")}").getOrElse("")
        ))
      case true =>
        jsValue.validate[A](parser)
    }
  }
}
