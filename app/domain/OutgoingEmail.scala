package domain

import java.time.OffsetDateTime
import java.util.UUID

import helpers.JavaTime
import play.api.libs.json._
import play.api.libs.mailer.{Attachment, Email}
import warwick.sso.Usercode

import scala.language.higherKinds

case class OutgoingEmail(
  id: Option[UUID] = None,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  email: Email,
  recipient: Option[Usercode],
  emailAddress: Option[String] = None,
  sent: Option[OffsetDateTime] = None,
  lastSendAttempt: Option[OffsetDateTime] = None,
  failureReason: Option[String] = None,
  updated: OffsetDateTime = JavaTime.offsetDateTime
)

object OutgoingEmail {
  // Attachments aren't attached to the persisted Email() but as a separate entity
  implicit val emailAttachmentFormatter: Format[Attachment] = Format(null, _ => JsNull)
  implicit val emailFormatter: Format[Email] = Json.format[Email]
}