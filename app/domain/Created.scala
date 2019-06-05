package domain

import java.time.OffsetDateTime

trait Created[A <: Created[A]] {
  def created: OffsetDateTime
}
