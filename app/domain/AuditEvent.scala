package domain

import java.time.ZonedDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import play.api.libs.json.JsValue
import slick.jdbc.PostgresProfile.api._
import warwick.sso.Usercode

case class AuditEvent(
  id: Option[UUID] = None,
  date: ZonedDateTime = ZonedDateTime.now(),
  operation: String,
  usercode: Option[Usercode],
  data: JsValue,
  targetId: String,
  targetType: String
)

object AuditEvent {
  class AuditEvents(tag: Tag) extends Table[AuditEvent](tag, "audit_event") {
    def id = column[UUID]("id", O.PrimaryKey)
    def date = column[ZonedDateTime]("event_date_utc")
    def operation = column[String]("operation")
    def usercode = column[Usercode]("usercode")
    def data = column[JsValue]("data")
    def targetId = column[String]("target_id")
    def targetType = column[String]("target_type")

    def * = (id.?, date, operation, usercode.?, data, targetId, targetType) <> ((AuditEvent.apply _).tupled, AuditEvent.unapply)
  }

  val auditEvents = TableQuery[AuditEvents]
}
