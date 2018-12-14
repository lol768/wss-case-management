package services.tabula

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import domain._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import warwick.sso.{UniversityID, Usercode}

object TabulaResponseParsers {

  object TabulaProfileData {

    case class StudentCourseYearDetails(
      academicYear: String,
      yearOfStudy: Int,
      studyLevel: Option[String],
      modeOfAttendance: String,
      enrolmentDepartment: SitsDepartment
    )
    val studentCourseYearDetailsReads: Reads[StudentCourseYearDetails] = (
      (__ \ "academicYear").read[String] and
      (__ \ "yearOfStudy").read[Int] and
      (__ \ "studyLevel").readNullable[String] and
      (__ \ "modeOfAttendance" \ "code").read[String] and
      (__ \ "enrolmentDepartment").read[SitsDepartment](departmentReads)
    )(StudentCourseYearDetails.apply _)
    val studentCourseYearDetailsFields: Seq[String] =
      Seq("academicYear", "yearOfStudy", "studyLevel", "modeOfAttendance.code", "enrolmentDepartment")

    case class StudentCourseDetails(
      mostSignificant: Boolean,
      beginDate: LocalDate,
      endDate: Option[LocalDate],
      expectedEndDate: Option[LocalDate],
      courseType: String,
      course: Course,
      level: Option[String],
      studentCourseYearDetails: Seq[StudentCourseYearDetails],
    )
    val studentCourseDetailsReads: Reads[StudentCourseDetails] = (
      (__ \ "mostSignificant").read[Boolean] and
      (__ \ "beginDate").read[LocalDate] and
      (__ \ "endDate").readNullable[LocalDate] and
      (__ \ "expectedEndDate").readNullable[LocalDate] and
      (__ \ "course" \ "type").read[String] and
      (__ \ "course").read[Course](courseReads) and
      (__ \ "levelCode").readNullable[String] and
      (__ \ "studentCourseYearDetails").read[Seq[StudentCourseYearDetails]](Reads.seq(studentCourseYearDetailsReads))
    )(StudentCourseDetails.apply _)
    val studentCourseDetailsFields: Seq[String] =
      Seq("mostSignificant", "beginDate", "endDate", "expectedEndDate", "course", "levelCode") ++
        studentCourseYearDetailsFields.map(f => s"studentCourseYearDetails.$f")

    case class Member(
      universityId: String,
      userId: String,
      fullName: String,
      dateOfBirth: LocalDate,
      phoneNumber: Option[String],
      email: Option[String],
      homeDepartment: SitsDepartment,
      tier4VisaRequirement: Option[Boolean],
      nationality: Option[String],
      secondNationality: Option[String],
      disability: Option[SitsDisability],
      disabilityFundingStatus: Option[SitsDisabilityFundingStatus],
      jobTitle: Option[String],
      address: Option[Address],
      studentCourseDetails: Option[Seq[StudentCourseDetails]],
      userType: String,
    ) {
      def toUserProfile: SitsProfile = {
        val latestScd = studentCourseDetails.flatMap(scds => scds.find(_.mostSignificant))
        val latestScyd = latestScd.flatMap(_.studentCourseYearDetails.lastOption)
        val department = latestScyd.map(_.enrolmentDepartment).getOrElse(homeDepartment)

        SitsProfile(
          universityID = UniversityID(universityId),
          usercode = Usercode(userId),
          fullName = fullName,
          dateOfBirth = dateOfBirth,
          phoneNumber = phoneNumber,
          warwickEmail = email,
          address = address,
          department = SitsDepartment(department.code, department.name),
          course = latestScd.map(_.course),
          attendance = latestScyd.map(_.modeOfAttendance).flatMap(Attendance.withNameOption),
          group = latestScd.map(_.courseType).flatMap(StudentGroup.withNameOption),
          yearOfStudy = latestScyd.map(scyd => YearOfStudy(scyd.yearOfStudy, scyd.studyLevel)),
          startDate = latestScd.map(_.beginDate),
          endDate = latestScd.flatMap { scd => scd.endDate.orElse(scd.expectedEndDate) },
          nationality = nationality,
          dualNationality = secondNationality,
          tier4VisaRequired = tier4VisaRequirement,
          disability = disability,
          disabilityFundingStatus = disabilityFundingStatus,
          jobTitle = jobTitle,
          photo = None,
          userType = UserType.withName(userType)
        )
      }
    }
    val memberReads: Reads[Member] = (
      (__ \ "member" \ "universityId").read[String] and
      (__ \ "member" \ "userId").read[String] and
      (__ \ "member" \ "fullName").read[String] and
      (__ \ "member" \ "dateOfBirth").read[LocalDate] and
      (__ \ "member" \ "mobileNumber").readNullable[String] and
      (__ \ "member" \ "email").readNullable[String] and
      (__ \ "member" \ "homeDepartment").read[SitsDepartment](departmentReads) and
      (__ \ "member" \ "tier4VisaRequirement").readNullable[Boolean] and
      (__ \ "member" \ "nationality").readNullable[String] and
      (__ \ "member" \ "secondNationality").readNullable[String] and
      (__ \ "member" \ "disability").readNullable[SitsDisability](disabilityReads) and
      (__ \ "member" \ "disabilityFundingStatus").readNullable[SitsDisabilityFundingStatus](disabilityFundingStatusReads) and
      (__ \ "member" \ "jobTitle").readNullable[String] and
      (__ \ "member" \ "currentAddress").readNullable[Address](addressReads) and
      (__ \ "member" \ "studentCourseDetails").readNullable[Seq[StudentCourseDetails]](Reads.seq(studentCourseDetailsReads)) and
      (__ \ "member" \ "userType").read[String]
    )(Member.apply _)
    val memberFields: Seq[String] =
      Seq(
        "universityId", "userId", "fullName", "dateOfBirth", "mobileNumber", "email", "homeDepartment",
        "tier4VisaRequirement", "nationality", "secondNationality", "jobTitle", "currentAddress", "userType"
      ).map(f => s"member.$f") ++
        disabilityFields.map(f => s"member.disability.$f") ++
        disabilityFundingStatusFields.map(f => s"member.disabilityFundingStatus.$f") ++
        addressFields.map(f => s"member.currentAddress.$f") ++
        studentCourseDetailsFields.map(f => s"member.studentCourseDetails.$f")
  }

  private val codeAndNameBuilder = (__ \ "code").read[String] and (__ \ "name").read[String]

  val departmentReads: Reads[SitsDepartment] = codeAndNameBuilder(SitsDepartment.apply _)
  val courseReads: Reads[Course] = codeAndNameBuilder((code, name) => Course.apply(code, s"${code.toUpperCase} $name"))

  val disabilityReads: Reads[SitsDisability] = (
    (__ \ "code").read[String] and
    (__ \ "definition").read[String] and
    (__ \ "sitsDefinition").read[String]
  )(SitsDisability.apply _)
  val disabilityFields: Seq[String] = Seq("code", "definition", "sitsDefinition")

  val disabilityFundingStatusReads: Reads[SitsDisabilityFundingStatus] = (
    (__ \ "code").read[String] and
    (__ \ "description").read[String]
  )(SitsDisabilityFundingStatus.apply _)
  val disabilityFundingStatusFields: Seq[String] = Seq("code", "description")

  val addressReads: Reads[Address] = (
    (__ \ "line1").readNullable[String] and
    (__ \ "line2").readNullable[String] and
    (__ \ "line3").readNullable[String] and
    (__ \ "line4").readNullable[String] and
    (__ \ "line5").readNullable[String] and
    (__ \ "postcode").readNullable[String]
  )(Address.apply _)
  val addressFields: Seq[String] = Seq("line1", "line2", "line3", "line4", "line5", "postcode")

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

  implicit val LocalDateTimeReads: Reads[LocalDateTime] = new Reads[LocalDateTime] {
    final val DateFormat = "yyyy-MM-dd'T'HH:mm:ss"

    override def reads(json: JsValue): JsResult[LocalDateTime] = json match {
      case JsString(s) => parseDateTime(s) match {
        case Some(d) => JsSuccess(d)
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.localdatetime.format", DateFormat))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.datetime"))))
    }

    private def parseDateTime(input: String): Option[LocalDateTime] =
      scala.util.control.Exception.nonFatalCatch[LocalDateTime].opt(
        LocalDateTime.parse(input, DateTimeFormatter.ofPattern(DateFormat))
      )
  }

  case class TabulaMemberSearchResult(
    universityID: UniversityID,
    usercode: Usercode,
    firstName: String,
    lastName: String,
    department: SitsDepartment,
    userType: String,
    photo: Option[String]
  ) extends Ordered[TabulaMemberSearchResult] {
    // Sort applicants to the bottom, and new applicants before others
    override def compare(that: TabulaMemberSearchResult): Int = {
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

  val memberSearchFields: Seq[String] =
    Seq("universityId", "userId", "firstName", "lastName", "department", "userType")
      .map(f => s"results.$f")

  val memberSearchResultReads: Reads[TabulaMemberSearchResult] = (
    (__ \ "universityId").read[String].map[UniversityID](UniversityID.apply) and
    (__ \ "userId").read[String].map[Usercode](Usercode.apply) and
    (__ \ "firstName").read[String] and
    (__ \ "lastName").read[String] and
    (__ \ "department").read[SitsDepartment](departmentReads) and
    (__ \ "userType").read[String] and
    Reads.pure(None)
  )(TabulaMemberSearchResult.apply _)

  val memberSearchResultsReads: Reads[Seq[TabulaMemberSearchResult]] = (__ \ "results").read[Seq[TabulaMemberSearchResult]](Reads.seq(memberSearchResultReads))

  case class TimetableEvent(
    start: LocalDateTime,
    end: LocalDateTime
  )
  val timetableEventReads: Reads[TimetableEvent] = Json.reads[TimetableEvent]
  val timetableEventsReads: Reads[Seq[TimetableEvent]] = (__ \ "events").read[Seq[TimetableEvent]](Reads.seq(timetableEventReads))

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
