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

  object Target {
    val ClientSummary = Symbol("ClientSummary")
    val Registration = Symbol("Registration")
    val Case = Symbol("Case")
    val CaseDocument = Symbol("CaseDocument")
    val Enquiry = Symbol("Enquiry")
    val Appointment = Symbol("Appointment")
    val UploadedFile = Symbol("UploadedFile")
    val Building = Symbol("Building")
    val Room = Symbol("Room")
    val OutgoingEmail = Symbol("OutgoingEmail")
  }

  object Operation {
    object ClientSummary {
      val Save = Symbol("SaveClientSummary")
      val Update = Symbol("UpdateClientSummary")
    }

    object Registration {
      val Save = Symbol("SaveRegistration")
      val Update = Symbol("UpdateRegistration")
      val SendInvite = Symbol("InviteToRegister")
    }

    object Case {
      val Save = Symbol("CaseSave")
      val ImportMigrated = Symbol("CaseImportMigrated")
      val Update = Symbol("CaseUpdate")
      val SetTags = Symbol("CaseSetTags")
      val AddLink = Symbol("CaseLinkSave")
      val DeleteLink = Symbol("CaseLinkDelete")
      val AddGeneralNote = Symbol("CaseAddGeneralNote")
      val UpdateNote = Symbol("CaseNoteUpdate")
      val DeleteNote = Symbol("CaseNoteDelete")
      val AddDocument = Symbol("CaseAddDocument")
      val DeleteDocument = Symbol("CaseDocumentDelete")
      val DownloadDocument = Symbol("CaseDocumentDownload")
      val AddMessage = Symbol("CaseAddMessage")
      val Reassign = Symbol("CaseReassign")
      val View = Symbol("CaseView")
      val SetOwners = Symbol("CaseSetOwners")

      def transition(state: IssueState): Symbol = state match {
        case IssueState.Open => Symbol("CaseOpen")
        case IssueState.Closed => Symbol("CaseClosed")
        case IssueState.Reopened => Symbol("CaseReopened")
      }
    }

    object Enquiry {
      val Save = Symbol("EnquirySave")
      val AddMessage = Symbol("EnquiryAddMessage")
      val Reassign = Symbol("EnquiryReassign")
      val SendClientReminder = Symbol("EnquirySendClientReminder")
      val View = Symbol("EnquiryView")
      val SetOwners = Symbol("EnquirySetOwners")

      def transition(state: IssueState): Symbol = state match {
        case IssueState.Open => Symbol("EnquiryOpen")
        case IssueState.Closed => Symbol("EnquiryClosed")
        case IssueState.Reopened => Symbol("EnquiryReopened")
      }

      def transitionWithMessage(state: IssueState): Symbol = state match {
        case IssueState.Open => Symbol("EnquiryOpenWithMessage")
        case IssueState.Closed => Symbol("EnquiryClosedWithMessage")
        case IssueState.Reopened => Symbol("EnquiryReopenedWithMessage")
      }
    }

    object Appointment {
      val Save = Symbol("AppointmentSave")
      val Update = Symbol("AppointmentUpdate")
      val Reschedule = Symbol("AppointmentReschedule")
      val Accept = Symbol("AppointmentAccept")
      val Decline = Symbol("AppointmentDecline")
      val Outcomes = Symbol("AppointmentOutcomes")
      val Cancel = Symbol("AppointmentCancel")
      val SendClientReminder = Symbol("AppointmentSendClientReminder")
      val View = Symbol("AppointmentView")
      val SetOwners = Symbol("AppointmentSetOwners")
    }

    object UploadedFile {
      val Save = Symbol("UploadedFileStore")
      val Delete = Symbol("UploadedFileDelete")
    }

    object Building {
      val Save = Symbol("BuildingSave")
      val Update = Symbol("BuildingUpdate")
    }

    object Room {
      val Save = Symbol("RoomSave")
      val Update = Symbol("RoomUpdate")
    }

    object OutgoingEmail {
      val Send = Symbol("SendEmail")
    }
  }
}
