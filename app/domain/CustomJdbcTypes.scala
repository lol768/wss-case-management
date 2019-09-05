package domain

import java.sql.Date
import java.time.{Duration, LocalDate}

import com.github.tminglei.slickpg.JsonString
import domain.ExtendedPostgresProfile.api._
import enumeratum.SlickEnumSupport
import play.api.libs.json.{Format, JsValue, Json}
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.slick.jdbctypes.JdbcDateTypesUtcColumnImplicits
import warwick.sso.{GroupName, UniversityID, Usercode}

import scala.reflect.ClassTag

object CustomJdbcTypes extends SlickEnumSupport with JdbcDateTypesUtcColumnImplicits {
  override val profile: JdbcProfile = ExtendedPostgresProfile
  import profile._

  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base[Usercode, String](
    u => u.string,
    s => Usercode(s)
  )

  implicit val universityIdTypeMapper: JdbcType[UniversityID] = MappedColumnType.base[UniversityID, String](
    u => u.string,
    s => UniversityID(s)
  )

  implicit val groupNameTypeMapper: JdbcType[GroupName] = MappedColumnType.base[GroupName, String](
    g => g.string,
    s => GroupName(s)
  )

  implicit val teamTypeMapper: JdbcType[Team] = MappedColumnType.base[Team, String](
    t => t.id,
    s => Teams.fromId(s)
  )

  implicit val issueKeyMapper: JdbcType[IssueKey] = MappedColumnType.base[IssueKey, String](
    k => k.string,
    s => IssueKey(s)
  )

  /** Maps a column to a generic JsValue type */
  implicit val jsValueMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, JsonString](
    (value: JsValue) =>  JsonString(Json.stringify(value).replace("\\u0000", "")),
    (js: JsonString) => Json.parse(js.value)
  )

  /** Maps a column to a specific class via its implicit JSON conversions */
  private def jsonTypeMapper[T : ClassTag](implicit ev: Format[T]): JdbcType[T] = MappedColumnType.base[T, JsValue](
    Json.toJson(_),
    _.as[T]
  )

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  implicit val durationMapper: JdbcType[Duration] = MappedColumnType.base[Duration, Long](_.getSeconds, Duration.ofSeconds)

  implicit val localDateColumnType: JdbcType[LocalDate] = MappedColumnType.base[LocalDate, Date](
    ld => Date.valueOf(ld),
    d => d.toLocalDate
  )

  // Json mappings
  implicit val userPreferencesMapper: JdbcType[UserPreferences] = jsonTypeMapper[UserPreferences]
  implicit val initialConsultationMapper: JdbcType[ClientConsultation] = jsonTypeMapper[ClientConsultation]

  // Enum[] mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val messageOwnerMapper: JdbcType[MessageOwner] = mappedColumnTypeForEnum(MessageOwner)
  implicit lazy val messageSenderMapper: JdbcType[MessageSender] = mappedColumnTypeForEnum(MessageSender)
  implicit lazy val issueStateMapper: JdbcType[IssueState] = mappedColumnTypeForEnum(IssueState)
  implicit lazy val caseTypeMapper: JdbcType[CaseType] = mappedColumnTypeForEnum(CaseType)
  implicit lazy val dsaFundingTypeMapper: JdbcType[DSAFundingType] = mappedColumnTypeForEnum(DSAFundingType)
  implicit lazy val dsaIneligibilityReasonMapper: JdbcType[DSAIneligibilityReason] = mappedColumnTypeForEnum(DSAIneligibilityReason)
  implicit lazy val caseCauseMapper: JdbcType[CaseCause] = mappedColumnTypeForEnum(CaseCause)
  implicit lazy val caseTagMapper: JdbcType[CaseTag] = mappedColumnTypeForEnum(CaseTag)
  implicit lazy val caseLinkTypeMapper: JdbcType[CaseLinkType] = mappedColumnTypeForEnum(CaseLinkType)
  implicit lazy val caseNoteTypeMapper: JdbcType[CaseNoteType] = mappedColumnTypeForEnum(CaseNoteType)
  implicit lazy val caseDocumentTypeMapper: JdbcType[CaseDocumentType] = mappedColumnTypeForEnum(CaseDocumentType)
  implicit lazy val ownerEntityTypeMapper: JdbcType[Owner.EntityType] = mappedColumnTypeForEnum(Owner.EntityType)
  implicit lazy val uploadedFileOwnerMapper: JdbcType[UploadedFileOwner] = mappedColumnTypeForEnum(UploadedFileOwner)
  implicit lazy val enquiryNoteTypeMapper: JdbcType[EnquiryNoteType] = mappedColumnTypeForEnum(EnquiryNoteType)
  implicit lazy val appointmentTypeMapper: JdbcType[AppointmentType] = mappedColumnTypeForEnum(AppointmentType)
  implicit lazy val appointmentPurposeMapper: JdbcType[AppointmentPurpose] = mappedColumnTypeForEnum(AppointmentPurpose)
  implicit lazy val appointmentStateMapper: JdbcType[AppointmentState] = mappedColumnTypeForEnum(AppointmentState)
  implicit lazy val appointmentCancellationReasonMapper: JdbcType[AppointmentCancellationReason] = mappedColumnTypeForEnum(AppointmentCancellationReason)
  implicit lazy val appointmentClientAttendanceStateMapper: JdbcType[AppointmentClientAttendanceState] = mappedColumnTypeForEnum(AppointmentClientAttendanceState)
  implicit lazy val appointmentOutcomeMapper: JdbcType[AppointmentOutcome] = mappedColumnTypeForEnum(AppointmentOutcome)
  implicit lazy val appointmentDSASupportAccessedMapper: JdbcType[AppointmentDSASupportAccessed] = mappedColumnTypeForEnum(AppointmentDSASupportAccessed)
  implicit lazy val clientRiskStatusMapper: JdbcType[ClientRiskStatus] = mappedColumnTypeForEnum(ClientRiskStatus)
  implicit lazy val reasonableAdjustmentMapper: JdbcType[ReasonableAdjustment] = mappedColumnTypeForEnum(ReasonableAdjustment)
  implicit lazy val severityOfProblemMapper: JdbcType[SeverityOfProblem] = mappedColumnTypeForEnum(SeverityOfProblem)
}
