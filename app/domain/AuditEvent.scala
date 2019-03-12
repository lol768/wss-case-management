package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import play.api.libs.json.JsValue
import warwick.core.helpers.JavaTime
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

  def latestEventsForUser(operation: Symbol, usercode: Usercode, targetType: Symbol): Query[(Rep[String], Rep[Option[OffsetDateTime]]), (String, Option[OffsetDateTime]), Seq] =
    auditEvents
      .filter(ae => ae.operation === operation && ae.usercode === usercode && ae.targetType === targetType)
      .groupBy(_.targetId)
      .map { case (targetId, ae) => (targetId, ae.map(_.date).max) }
}
