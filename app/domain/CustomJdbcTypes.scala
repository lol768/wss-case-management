package domain

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneId}

import play.api.libs.json.{JsValue, Json}
import slick.jdbc.JdbcType
import slick.jdbc.OracleProfile.api._
import warwick.sso.Usercode

object CustomJdbcTypes {
  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base[Usercode, String](
    u => u.string,
    s => Usercode(s)
  )

  implicit val localDateTimeTypeMapper: JdbcType[LocalDateTime] = MappedColumnType.base[LocalDateTime, Timestamp](
    dt => Timestamp.from(dt.atZone(ZoneId.systemDefault).toInstant),
    ts => LocalDateTime.ofInstant(ts.toInstant, ZoneId.systemDefault)
  )

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)
}
