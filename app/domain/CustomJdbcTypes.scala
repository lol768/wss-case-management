package domain

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}

import play.api.libs.json.{JsValue, Json}
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._
import warwick.sso.{UniversityID, Usercode}

object CustomJdbcTypes {
  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base[Usercode, String](
    u => u.string,
    s => Usercode(s)
  )

  implicit val universityIdTypeMapper: JdbcType[UniversityID] = MappedColumnType.base[UniversityID, String](
    u => u.string,
    s => UniversityID(s)
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
}
