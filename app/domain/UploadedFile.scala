package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.Usercode

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
  uploadedBy: Usercode
)
