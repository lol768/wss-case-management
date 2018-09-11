package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import helpers.JavaTime
import play.api.libs.json.JsValue
import slick.jdbc.PostgresProfile.api._
import warwick.sso.Usercode

case class AuditEvent(
  id: UUID,
  date: OffsetDateTime = JavaTime.offsetDateTime,
  operation: Symbol,
  usercode: Option[Usercode],
  data: JsValue,
  targetId: String,
  targetType: Symbol
)

object AuditEvent {
  def tupled = (apply _).tupled

  class AuditEvents(tag: Tag) extends Table[AuditEvent](tag, "audit_event") {
    def id = column[UUID]("id", O.PrimaryKey)
    def date = column[OffsetDateTime]("event_date_utc")
    def operation = column[Symbol]("operation")
    def usercode = column[Usercode]("usercode")
    def data = column[JsValue]("data")
    def targetId = column[String]("target_id")
    def targetType = column[Symbol]("target_type")

    def * = (id, date, operation, usercode.?, data, targetId, targetType).mapTo[AuditEvent]
  }

  val auditEvents = TableQuery[AuditEvents]
}
