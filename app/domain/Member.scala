package domain

import java.time.OffsetDateTime

import warwick.sso.Usercode

case class Member(
  usercode: Usercode,
  fullName: Option[String],
  lastUpdated: OffsetDateTime
) extends Ordered[Member] {
  val safeFullName: String = fullName.getOrElse(usercode.string)

  override def compare(that: Member): Int =
    if (this.fullName.nonEmpty && that.fullName.isEmpty) {
      -1
    } else if (that.fullName.nonEmpty && this.fullName.isEmpty) {
      1
    } else if (this.fullName.isEmpty && that.fullName.isEmpty) {
      this.usercode.string.compare(that.usercode.string)
    } else {
      this.fullName.get.compare(that.fullName.get)
    }
}
