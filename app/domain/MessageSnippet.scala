package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import warwick.fileuploads.UploadedFile

case class MessageSnippet(
  id: UUID,
  title: String,
  body: String,
  files: Seq[UploadedFile],
  lastUpdated: OffsetDateTime,
)

case class MessageSnippetSave(
  title: String,
  body: String,
)

object MessageSnippetSave {
  implicit val formatter: OFormat[MessageSnippetSave] = Json.format[MessageSnippetSave]
}
