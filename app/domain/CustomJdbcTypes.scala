package domain

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.{Calendar, TimeZone}

import domain.dao.GeneratedId
import enumeratum.SlickEnumSupport
import helpers.JavaTime
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{JdbcProfile, JdbcType, PostgresProfile}
import warwick.sso.{GroupName, UniversityID, Usercode}

object CustomJdbcTypes extends SlickEnumSupport {
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

  implicit val offsetDateTimeTypeMapper: JdbcType[OffsetDateTime] = new DriverJdbcType[OffsetDateTime]() {
    private[this] val referenceCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    private[this] val literalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private[this] def map(v: OffsetDateTime): Timestamp = Timestamp.from(v.toInstant)
    private[this] def comap(ts: Timestamp): OffsetDateTime = OffsetDateTime.ofInstant(ts.toInstant, JavaTime.timeZone)

    def sqlType: Int = java.sql.Types.TIMESTAMP
    def setValue(v: OffsetDateTime, p: PreparedStatement, idx: Int): Unit = p.setTimestamp(idx, map(v), referenceCalendar)
    def getValue(r: ResultSet, idx: Int): OffsetDateTime = {
      val v = r.getTimestamp(idx, referenceCalendar)

      if ((v eq null) || wasNull(r, idx)) null
      else comap(v)
    }
    def updateValue(v: OffsetDateTime, r: ResultSet, idx: Int): Unit = r.updateTimestamp(idx, map(v))
    override def valueToSQLLiteral(value: OffsetDateTime): String =
      s"{ts '${value.atZoneSameInstant(ZoneOffset.UTC).format(literalDateTimeFormatter)}'}"
  }

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  // Enum[] mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val messageOwnerMapper: JdbcType[MessageOwner] = mappedColumnTypeForEnum(MessageOwner)
  implicit lazy val messageSenderMapper: JdbcType[MessageSender] = mappedColumnTypeForEnum(MessageSender)
  implicit lazy val issueStateMapper: JdbcType[IssueState] = mappedColumnTypeForEnum(IssueState)
  implicit lazy val caseTypeMapper: JdbcType[CaseType] = mappedColumnTypeForEnum(CaseType)
  implicit lazy val caseCauseMapper: JdbcType[CaseCause] = mappedColumnTypeForEnum(CaseCause)
}
