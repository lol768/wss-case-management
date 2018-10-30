package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json._
import play.api.libs.mailer.{Attachment, Email}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

case class OutgoingEmail(
  id: Option[UUID] = None,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  email: Email,
  recipient: Option[Usercode] = None,
  emailAddress: Option[String] = None,
  sent: Option[OffsetDateTime] = None,
  lastSendAttempt: Option[OffsetDateTime] = None,
  failureReason: Option[String] = None,
  updated: OffsetDateTime = JavaTime.offsetDateTime
) {
  require(recipient.nonEmpty || emailAddress.nonEmpty, "One of recipient or emailAddress must be set")
  require(email.attachments.isEmpty, "Storing emails directly with attachments is not yet supported")
  require(email.to.isEmpty, "Don't use TO on the Email, set recipient or emailAddress")
}

object OutgoingEmail {
  // Attachments aren't attached to the persisted Email() but as a separate entity
  implicit val emailAttachmentFormatter: Format[Attachment] = Format(null, _ => JsNull)
  implicit val emailFormatter: Format[Email] = Json.format[Email]
}