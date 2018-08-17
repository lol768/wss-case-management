package domain

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.{LocalDateTime, OffsetDateTime, ZoneId}
import java.util.{Calendar, GregorianCalendar, SimpleTimeZone, TimeZone}

import enumeratum.SlickEnumSupport
import helpers.JavaTime
import play.api.libs.json.{JsValue, Json}
import slick.ast.BaseTypedType
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, JdbcType, PostgresProfile}
import warwick.sso.{GroupName, UniversityID, Usercode}

import scala.reflect._

object CustomJdbcTypes extends warwick.slick.jdbctypes.CustomJdbcTypes(PostgresProfile) with SlickEnumSupport {
  override val profile: JdbcProfile = PostgresProfile
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

  implicit val localDateTimeTypeMapper: JdbcType[LocalDateTime] = MappedColumnType.base[LocalDateTime, Timestamp](
    dt => Timestamp.from(dt.atZone(ZoneId.systemDefault).toInstant),
    ts => LocalDateTime.ofInstant(ts.toInstant, ZoneId.systemDefault)
  )

//  implicit val offsetDateTimeTypeMapper: JdbcType[OffsetDateTime] = new DriverJdbcType[OffsetDateTime]() {
//    private[this] def referenceCalendar= Calendar.getInstance(TimeZone.getTimeZone("UTC"))
//
//    private[this] def map(v: OffsetDateTime): Timestamp = Timestamp.from(v.toInstant)
//    private[this] def comap(ts: Timestamp): OffsetDateTime = OffsetDateTime.ofInstant(ts.toInstant, JavaTime.timeZone)
//
//    def sqlType: Int = java.sql.Types.TIMESTAMP
//    def setValue(v: OffsetDateTime, p: PreparedStatement, idx: Int): Unit = p.setTimestamp(idx, map(v), referenceCalendar)
//    def getValue(r: ResultSet, idx: Int): OffsetDateTime = comap(r.getTimestamp(idx, referenceCalendar))
//    def updateValue(v: OffsetDateTime, r: ResultSet, idx: Int): Unit = r.updateTimestamp(idx, map(v))
//    override def valueToSQLLiteral(value: OffsetDateTime): String = timestampColumnType.valueToSQLLiteral(map(value))
//  }

  class UtcTimestampJdbcType extends DriverJdbcType[Timestamp] {
    private[this] def referenceCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    def sqlType: Int = java.sql.Types.TIMESTAMP
    def setValue(v: Timestamp, p: PreparedStatement, idx: Int): Unit = p.setTimestamp(idx, v, referenceCalendar)
    def getValue(r: ResultSet, idx: Int): Timestamp = r.getTimestamp(idx, referenceCalendar)
    def updateValue(v: Timestamp, r: ResultSet, idx: Int): Unit = r.updateTimestamp(idx, v)

    // Force use of bind parameter rather than literal in WHERE clauses.
    override def hasLiteralForm = false

    /** Bug: Literal string is in JVM local time - should be same zone as [[referenceCalendar]] */
    // override def valueToSQLLiteral(value: Timestamp): String = s"{ts '${formatLiteral(value)}'}"
  }

  val utcTimestampColumnType = new UtcTimestampJdbcType

  implicit val offsetDateTimeTypeMapper: JdbcType[OffsetDateTime] = new MappedJdbcType[OffsetDateTime, Timestamp]()(utcTimestampColumnType, classTag[OffsetDateTime]) with BaseTypedType[OffsetDateTime] {
    def map(dt: OffsetDateTime): Timestamp = Timestamp.from(dt.toInstant)
    def comap(ts: Timestamp): OffsetDateTime = OffsetDateTime.ofInstant(ts.toInstant, JavaTime.timeZone)
  }

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  // Enum[] mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val messageOwnerMapper: JdbcType[MessageOwner] = mappedColumnTypeForEnum(MessageOwner)
  implicit lazy val messageSenderMapper: JdbcType[MessageSender] = mappedColumnTypeForEnum(MessageSender)
  implicit lazy val enquiryStateMapper: JdbcType[EnquiryState] = mappedColumnTypeForEnum(EnquiryState)
}
