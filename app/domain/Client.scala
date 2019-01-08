package domain

import java.time.OffsetDateTime

import warwick.sso.UniversityID

case class Client(
  universityID: UniversityID,
  fullName: Option[String],
  lastUpdated: OffsetDateTime
) extends Ordered[Client] {
  val safeFullName: String = fullName.getOrElse(universityID.string)

  override def compare(that: Client): Int =
    if (this.fullName.nonEmpty && that.fullName.isEmpty) {
      -1
    } else if (that.fullName.nonEmpty && this.fullName.isEmpty) {
      1
    } else if (this.fullName.isEmpty && that.fullName.isEmpty) {
      this.universityID.string.compare(that.universityID.string)
    } else {
      this.fullName.get.compare(that.fullName.get)
    }
}