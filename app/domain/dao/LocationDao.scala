package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.LocationDao._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

@ImplementedBy(classOf[LocationDaoImpl])
trait LocationDao {
  def insert(building: StoredBuilding)(implicit ac: AuditLogContext): DBIO[StoredBuilding]
  def update(building: StoredBuilding, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredBuilding]
  def insert(room: StoredRoom)(implicit ac: AuditLogContext): DBIO[StoredRoom]
  def update(room: StoredRoom, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredRoom]

  def findBuildingsQuery: Query[Buildings, StoredBuilding, Seq]
  def findRoomsQuery: Query[Rooms, StoredRoom, Seq]
}

@Singleton
class LocationDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends LocationDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(building: StoredBuilding)(implicit ac: AuditLogContext): DBIO[StoredBuilding] =
    buildings.insert(building)

  override def update(building: StoredBuilding, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredBuilding] =
    buildings.update(building.atVersion(version))

  override def insert(room: StoredRoom)(implicit ac: AuditLogContext): DBIO[StoredRoom] =
    rooms.insert(room)

  override def update(room: StoredRoom, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredRoom] =
    rooms.update(room.atVersion(version))

  override def findBuildingsQuery: Query[Buildings, StoredBuilding, Seq] =
    buildings.table

  override def findRoomsQuery: Query[Rooms, StoredRoom, Seq] =
    rooms.table

}

object LocationDao {
  val buildings: VersionedTableQuery[StoredBuilding, StoredBuildingVersion, Buildings, BuildingVersions] =
    VersionedTableQuery(TableQuery[Buildings], TableQuery[BuildingVersions])

  val rooms: VersionedTableQuery[StoredRoom, StoredRoomVersion, Rooms, RoomVersions] =
    VersionedTableQuery(TableQuery[Rooms], TableQuery[RoomVersions])

  case class StoredBuilding(
    id: UUID,
    name: String,
    wai2GoID: Int,
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredBuilding] {
    def asBuilding: Building = Building(id, name, wai2GoID, created, version)

    override def atVersion(at: OffsetDateTime): StoredBuilding = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredBuilding]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredBuildingVersion(
        id,
        name,
        wai2GoID,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredBuildingVersion(
    id: UUID,
    name: String,
    wai2GoID: Int,
    created: OffsetDateTime,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode],
  ) extends StoredVersion[StoredBuilding]

  trait CommonBuildingProperties { self: Table[_] =>
    def name = column[String]("name")
    def wai2GoID = column[Int]("wai2go_id")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Buildings(tag: Tag) extends Table[StoredBuilding](tag, "location_building")
    with VersionedTable[StoredBuilding]
    with CommonBuildingProperties {
    override def matchesPrimaryKey(other: StoredBuilding): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredBuilding] =
      (id, name, wai2GoID, created, version).mapTo[StoredBuilding]
    def building =
      (id, name, wai2GoID, created, version).mapTo[Building]
  }

  class BuildingVersions(tag: Tag) extends Table[StoredBuildingVersion](tag, "location_building_version")
    with StoredVersionTable[StoredBuilding]
    with CommonBuildingProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredBuildingVersion] =
      (id, name, wai2GoID, created, version, operation, timestamp, auditUser).mapTo[StoredBuildingVersion]
    def pk = primaryKey("pk_building_version", (id, timestamp))
    def idx = index("idx_building_version", (id, version))
  }

  implicit class BuildingExtensions[C[_]](q: Query[Buildings, StoredBuilding, C]) {
    def withRooms = q
      .join(rooms.table)
      .on(_.id === _.buildingID)
  }

  case class StoredRoom(
    id: UUID,
    buildingID: UUID,
    name: String,
    wai2GoID: Option[Int],
    available: Boolean,
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredRoom] {
    def asRoom(building: Building): Room = Room(
      id,
      building,
      name,
      wai2GoID.getOrElse(building.wai2GoID),
      available,
      created,
      version
    )

    override def atVersion(at: OffsetDateTime): StoredRoom = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredRoom]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredRoomVersion(
        id,
        buildingID,
        name,
        wai2GoID,
        available,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredRoomVersion(
    id: UUID,
    buildingID: UUID,
    name: String,
    wai2GoID: Option[Int],
    available: Boolean,
    created: OffsetDateTime,
    version: OffsetDateTime,

    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode],
  ) extends StoredVersion[StoredRoom]

  trait CommonRoomProperties { self: Table[_] =>
    def buildingID = column[UUID]("building_id")
    def name = column[String]("name")
    def wai2GoID = column[Option[Int]]("wai2go_id")
    def available = column[Boolean]("is_available")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Rooms(tag: Tag) extends Table[StoredRoom](tag, "location_room")
    with VersionedTable[StoredRoom]
    with CommonRoomProperties {
    override def matchesPrimaryKey(other: StoredRoom): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredRoom] =
      (id, buildingID, name, wai2GoID, available, created, version).mapTo[StoredRoom]
    def fk = foreignKey("fk_location_room_building", buildingID, buildings.table)(_.id)
    def idx = index("idx_location_room_building", buildingID)
  }

  class RoomVersions(tag: Tag) extends Table[StoredRoomVersion](tag, "location_room_version")
    with StoredVersionTable[StoredRoom]
    with CommonRoomProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredRoomVersion] =
      (id, buildingID, name, wai2GoID, available, created, version, operation, timestamp, auditUser).mapTo[StoredRoomVersion]
    def pk = primaryKey("pk_room_version", (id, timestamp))
    def idx = index("idx_room_version", (id, version))
  }

  implicit class RoomExtensions[C[_]](q: Query[Rooms, StoredRoom, C]) {
    def withBuilding = q
      .join(buildings.table)
      .on(_.buildingID === _.id)
  }

  implicit class RoomBuildingExtensions[C[_]](q: Query[(Rooms, Buildings), (StoredRoom, StoredBuilding), C]) {
    def mapToRoom = q
      .map { case (r, b) =>
        (r.id, b.building, r.name, r.wai2GoID.getOrElse(b.wai2GoID), r.available, r.created, r.version).mapTo[Room]
      }
  }
}