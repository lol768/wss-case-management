package domain

import play.api.libs.json.{Json, Writes}


object IdAndDescription {
  val writer: Writes[IdAndDescription] = (o: IdAndDescription) => Json.obj(
    "id" -> o.id,
    "description" -> o.description
  )
}

trait IdAndDescription {
  val id: String
  val description: String
}
