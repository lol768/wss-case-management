package domain

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import controllers.refiners.EnquirySpecificRequest
import enumeratum.{EnumEntry, PlayEnum}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import warwick.sso.{AuthenticatedRequest, Usercode}

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

object UploadedFileSave {
  def seqFromRequest(request: AuthenticatedRequest[MultipartFormData[TemporaryFile]]): Seq[(ByteSource, UploadedFileSave)] =
    request.body.files.filter(_.filename.nonEmpty).map { file =>
      (Files.asByteSource(file.ref), UploadedFileSave(
        file.filename,
        file.ref.length(),
        file.contentType.getOrElse("application/octet-stream"),
        request.context.user.get.usercode
      ))
    }
}

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  case object Message extends UploadedFileOwner

  val values: immutable.IndexedSeq[UploadedFileOwner] = findValues
}