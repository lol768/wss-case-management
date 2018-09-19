package domain

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.{Calendar, TimeZone}

import enumeratum.SlickEnumSupport
import helpers.JavaTime
import play.api.libs.json.{JsValue, Json}
import ExtendedPostgresProfile.api._
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.slick.jdbctypes.JdbcDateTypesUtc
import warwick.sso.{GroupName, UniversityID, Usercode}

object CustomJdbcTypes extends SlickEnumSupport with JdbcDateTypesUtc {
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

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  // Enum[] mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val messageOwnerMapper: JdbcType[MessageOwner] = mappedColumnTypeForEnum(MessageOwner)
  implicit lazy val messageSenderMapper: JdbcType[MessageSender] = mappedColumnTypeForEnum(MessageSender)
  implicit lazy val issueStateMapper: JdbcType[IssueState] = mappedColumnTypeForEnum(IssueState)
  implicit lazy val caseTypeMapper: JdbcType[CaseType] = mappedColumnTypeForEnum(CaseType)
  implicit lazy val caseCauseMapper: JdbcType[CaseCause] = mappedColumnTypeForEnum(CaseCause)
  implicit lazy val caseTagMapper: JdbcType[CaseTag] = mappedColumnTypeForEnum(CaseTag)
  implicit lazy val caseLinkTypeMapper: JdbcType[CaseLinkType] = mappedColumnTypeForEnum(CaseLinkType)
  implicit lazy val caseNoteTypeMapper: JdbcType[CaseNoteType] = mappedColumnTypeForEnum(CaseNoteType)
  implicit lazy val caseDocumentTypeMapper: JdbcType[CaseDocumentType] = mappedColumnTypeForEnum(CaseDocumentType)
  implicit lazy val ownerEntityTypeMapper: JdbcType[Owner.EntityType] = mappedColumnTypeForEnum(Owner.EntityType)
  implicit lazy val uploadedFileOwnerMapper: JdbcType[UploadedFileOwner] = mappedColumnTypeForEnum(UploadedFileOwner)
  implicit lazy val enquiryNoteTypeMapper: JdbcType[EnquiryNoteType] = mappedColumnTypeForEnum(EnquiryNoteType)
}
