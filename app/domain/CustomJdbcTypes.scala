package domain

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}

import enumeratum.SlickEnumSupport
import play.api.libs.json.{JsValue, Json}
import slick.ast.BaseTypedType
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, JdbcType, PostgresProfile}
import warwick.sso.{GroupName, UniversityID, Usercode}

object CustomJdbcTypes extends warwick.slick.jdbctypes.CustomJdbcTypes(PostgresProfile) with SlickEnumSupport {
  override val profile: JdbcProfile = PostgresProfile

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

  implicit val localDateTimeTypeMapper: JdbcType[LocalDateTime] = MappedColumnType.base[LocalDateTime, Timestamp](
    dt => Timestamp.from(dt.atZone(ZoneId.systemDefault).toInstant),
    ts => LocalDateTime.ofInstant(ts.toInstant, ZoneId.systemDefault)
  )

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  // Enum[] mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val messageOwnerMapper: JdbcType[MessageOwner] = mappedColumnTypeForEnum(MessageOwner)
  implicit lazy val messageSenderMapper: JdbcType[MessageSender] = mappedColumnTypeForEnum(MessageSender)
  implicit lazy val enquiryStateMapper: JdbcType[EnquiryState] = mappedColumnTypeForEnum(EnquiryState)
}
