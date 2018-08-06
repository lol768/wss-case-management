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
  class AuditEvents(tag: Tag) extends Table[AuditEvent](tag, "AUDIT_EVENT") {
    def id = column[UUID]("ID", O.PrimaryKey)
    def date = column[ZonedDateTime]("EVENT_DATE_UTC")
    def operation = column[String]("OPERATION")
    def usercode = column[Usercode]("USERCODE")
    def data = column[JsValue]("DATA")
    def targetId = column[String]("TARGET_ID")
    def targetType = column[String]("TARGET_TYPE")

    def * = (id.?, date, operation, usercode.?, data, targetId, targetType) <> ((AuditEvent.apply _).tupled, AuditEvent.unapply)
  }

  val auditEvents = TableQuery[AuditEvents]
}
