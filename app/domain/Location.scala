package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, Writes}
import warwick.sso.Usercode

case class Building(
  id: UUID,
  name: String,
  wai2GoID: Int,
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) extends Created

object Building {
  def tupled = (apply _).tupled

  val NameMaxLength = 200
}

case class BuildingSave(
  name: String,
  wai2GoID: Int
)

case class Room(
  id: UUID,
  building: Building,
  name: String,
  wai2GoID: Int,
  available: Boolean,
  o365Usercode: Option[Usercode],
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) extends Created

object Room {
  def tupled = (apply _).tupled

  val NameMaxLength = 200

  val writer: Writes[Room] = o => Json.obj(
    "id" -> o.id.toString,
    "name" -> o.name,
    "building" -> o.building.name,
    "o365Usercode" -> o.o365Usercode.map(_.string),
    "locationId" -> o.wai2GoID,
  )
}

case class RoomSave(
  buildingID: UUID,
  name: String,
  wai2GoID: Option[Int],
  available: Boolean,
  o365Usercode: Option[Usercode],
)
