package domain

import java.time.OffsetDateTime
import java.util.UUID

trait Issue {
  def id: Option[UUID]
  def state: IssueState
  def team: Team
}

case class IssueRender(
  issue: Issue,
  messages: Seq[MessageRender],
  lastUpdatedDate: OffsetDateTime
)
