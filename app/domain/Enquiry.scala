package domain

import java.time.OffsetDateTime
import java.util.UUID

import enumeratum.{EnumEntry, PlayEnum}
import services.EnquiryService
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, Usercode}

import scala.collection.immutable
import scala.language.higherKinds

case class Enquiry(
  id: UUID,
  key: IssueKey,
  client: Client,
  subject: String,
  team: Team,
  state: IssueState,
  lastUpdated: OffsetDateTime,
  created: OffsetDateTime
) extends Issue with Created

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

case class EnquiryListRender(
  enquiry: Enquiry,
  lastUpdated: OffsetDateTime,
  lastClientMessage: Option[OffsetDateTime],
  lastViewed: Option[OffsetDateTime],
) extends IssueListRender(enquiry)

case class EnquiryNote(
  id: UUID,
  noteType: EnquiryNoteType,
  text: String,
  teamMember: Member,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  lastUpdated: OffsetDateTime = JavaTime.offsetDateTime,
) extends Created

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


