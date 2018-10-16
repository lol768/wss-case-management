package domain

import java.time.OffsetDateTime
import java.util.UUID

import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.data.Form
import play.api.data.Forms._
import services.EnquiryService
import warwick.sso.{UniversityID, Usercode}

import scala.collection.immutable
import scala.language.higherKinds

object Enquiry {

  val SubjectMaxLength = 200

  case class FormData(
    subject: String,
    text: String
  )

  val form = Form(mapping(
    "subject" -> nonEmptyText(maxLength = Enquiry.SubjectMaxLength),
    "text" -> nonEmptyText
  )(FormData.apply)(FormData.unapply))

}

case class Enquiry(
  id: Option[UUID],
  key: IssueKey,
  client: Client,
  subject: String,
  team: Team,
  state: IssueState,
  lastUpdated: OffsetDateTime,
  created: OffsetDateTime
) extends Issue

case class EnquirySave(
  universityID: UniversityID,
  subject: String,
  team: Team,
  state: IssueState
)

case class EnquiryRender(
  enquiry: Enquiry,
  messages: Seq[MessageRender],
  notes: Seq[EnquiryNote]
) {
  def toIssue = IssueRender(
    enquiry,
    messages,
    EnquiryService.lastModified(this)
  )
}

case class EnquiryNote(
  id: UUID,
  noteType: EnquiryNoteType,
  text: String,
  teamMember: Member,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

object EnquiryNote {
  // oldest first
  val dateOrdering: Ordering[EnquiryNote] = Ordering.by[EnquiryNote, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

/**
  * Just the data of a enquiry note required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class EnquiryNoteSave(
  text: String,
  teamMember: Usercode
)

sealed abstract class EnquiryNoteType(val description: String) extends EnumEntry
object EnquiryNoteType extends PlayEnum[EnquiryNoteType] {
  case object Referral extends EnquiryNoteType("Referral")

  override def values: immutable.IndexedSeq[EnquiryNoteType] = findValues
}


