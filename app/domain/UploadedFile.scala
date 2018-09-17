package domain

import java.time.OffsetDateTime
import java.util.UUID

import enumeratum.{EnumEntry, PlayEnum}
import warwick.sso.Usercode

import scala.collection.immutable

case class UploadedFile(
  id: UUID, // Files are stored flat in the object store container by UUID
  fileName: String,
  contentLength: Long,
  contentType: String,
  uploadedBy: Usercode,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

/**
  * Just the metadata of an uploaded file required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class UploadedFileSave(
  fileName: String,
  contentLength: Long,
  contentType: String,
  uploadedBy: Usercode,
)

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  case object Message extends UploadedFileOwner

  val values: immutable.IndexedSeq[UploadedFileOwner] = findValues
}