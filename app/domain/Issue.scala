package domain

import java.time.OffsetDateTime
import java.util.UUID

import helpers.FormHelpers
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import warwick.core.helpers.JavaTime

trait Issue {
  def id: UUID
  def state: IssueState
  def team: Team
}

case class IssueRender(
  issue: Issue,
  messages: Seq[MessageRender],
  lastUpdatedDate: OffsetDateTime
)

abstract class IssueListRender(val issue: Issue) {
  val lastUpdated: OffsetDateTime
  val lastClientMessage: Option[OffsetDateTime]
  val lastViewed: Option[OffsetDateTime]

  def hasUnreadClientMessage: Boolean =
    lastClientMessage.exists { lastMessage => lastViewed.isEmpty || lastViewed.exists(_.isBefore(lastMessage)) }
}

case class IssueListFilter(
  lastUpdatedAfter: Option[OffsetDateTime] = None,
  lastUpdatedBefore: Option[OffsetDateTime] = None,
  hasUnreadClientMessages: Option[Boolean] = None,
) {
  val nonEmpty: Boolean =
    lastUpdatedAfter.nonEmpty ||
      !lastUpdatedBefore.forall(_.toLocalDate == JavaTime.localDate) ||
      hasUnreadClientMessages.nonEmpty

  def withLastUpdatedBetween(from: OffsetDateTime, to: OffsetDateTime): IssueListFilter = {
    require(to.isAfter(from), "To date must be after from date")
    copy(lastUpdatedAfter = Some(from), lastUpdatedBefore = Some(to))
  }
  def withHasUnreadClientMessages(hasUnreads: Boolean): IssueListFilter = copy(hasUnreadClientMessages = Some(hasUnreads))
}

object IssueListFilter {
  val empty: IssueListFilter = apply()

  val form: Form[IssueListFilter] = Form(
    mapping(
      // This seems convoluted but currently datePicker sends a time component (CASE-485)
      "lastUpdatedAfter" -> optional(FormHelpers.offsetDateTime.transform[OffsetDateTime](_.toLocalDate.atTime(0, 0).atZone(JavaTime.timeZone).toOffsetDateTime, identity)),
      "lastUpdatedBefore" -> optional(FormHelpers.offsetDateTime.transform[OffsetDateTime](_.toLocalDate.atTime(23, 59, 59, 999000000).atZone(JavaTime.timeZone).toOffsetDateTime, identity)),
      "hasUnreadClientMessages" -> optional(boolean),
    )(IssueListFilter.apply)(IssueListFilter.unapply)
      .verifying(Constraint { filter: IssueListFilter =>
        (filter.lastUpdatedAfter, filter.lastUpdatedBefore) match {
          case (Some(start), Some(end)) if end.isBefore(start) => Invalid(Seq(ValidationError("error.dates.endBeforeStart")))
          case _ => Valid
        }
      })
  )
}