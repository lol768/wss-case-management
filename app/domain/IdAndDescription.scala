package domain

import play.api.libs.json.{Json, Writes}

object IdAndDescription {
  val writer: Writes[IdAndDescription] = (o: IdAndDescription) => Json.obj(
    "id" -> o.id,
    "description" -> o.description
  )

  private[IdAndDescription] case class GeneratedIdAndDescription(id: String, description: String) extends IdAndDescription
  def apply(id: String, description: String): IdAndDescription = GeneratedIdAndDescription(id, description)
}

trait IdAndDescription {
  val id: String
  val description: String
}
