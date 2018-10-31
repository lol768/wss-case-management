package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.ExtendedPostgresProfile.api._
import domain.dao.LocationDao.{StoredBuilding, StoredRoom}
import domain.dao.{DaoRunner, LocationDao}
import domain.{Building, BuildingSave, Room, RoomSave}
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[LocationServiceImpl])
trait LocationService {
  def create(building: BuildingSave)(implicit ac: AuditLogContext): Future[ServiceResult[Building]]
  def update(buildingID: UUID, building: BuildingSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Building]]

  def create(room: RoomSave)(implicit ac: AuditLogContext): Future[ServiceResult[Room]]
  def update(roomID: UUID, room: RoomSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Room]]

  def findBuilding(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Building]]
  def findRoom(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Room]]

  def allBuildings(implicit t: TimingContext): Future[ServiceResult[Seq[Building]]]
  def allRooms(implicit t: TimingContext): Future[ServiceResult[Seq[Room]]]
  def availableRooms(implicit t: TimingContext): Future[ServiceResult[Seq[Room]]]
}

@Singleton
class LocationServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: LocationDao,
)(implicit ec: ExecutionContext) extends LocationService with Logging {

  private def createStoredBuilding(id: UUID, building: BuildingSave): StoredBuilding =
    StoredBuilding(
      id,
      building.name,
      building.wai2GoID,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime
    )

  override def create(building: BuildingSave)(implicit ac: AuditLogContext): Future[ServiceResult[Building]] = {
    val id = UUID.randomUUID()
    auditService.audit('BuildingSave, id.toString, 'Building, Json.obj()) {
      daoRunner.run(dao.insert(createStoredBuilding(id, building)))
        .map { b => Right(b.asBuilding) }
    }
  }

  override def update(buildingID: UUID, building: BuildingSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Building]] =
    auditService.audit('BuildingUpdate, buildingID.toString, 'Building, Json.obj()) {
      daoRunner.run(for {
        existing <- dao.findBuildingsQuery.filter(_.id === buildingID).result.head
        updated <- dao.update(
          StoredBuilding(
            existing.id,
            building.name,
            building.wai2GoID,
            existing.created,
            JavaTime.offsetDateTime
          ),
          version
        )
      } yield updated).map { b => Right(b.asBuilding) }
    }

  private def createStoredRoom(id: UUID, room: RoomSave): StoredRoom =
    StoredRoom(
      id,
      room.buildingID,
      room.name,
      room.wai2GoID,
      room.available,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime
    )

  override def create(room: RoomSave)(implicit ac: AuditLogContext): Future[ServiceResult[Room]] = {
    val id = UUID.randomUUID()
    auditService.audit('RoomSave, id.toString, 'Room, Json.obj()) {
      daoRunner.run(for {
        created <- dao.insert(createStoredRoom(id, room))
        building <- dao.findBuildingsQuery.filter(_.id === room.buildingID).map(_.building).result.head
      } yield (created, building))
        .map { case (r, b) => Right(r.asRoom(b)) }
    }
  }

  override def update(roomID: UUID, room: RoomSave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Room]] =
    auditService.audit('RoomUpdate, roomID.toString, 'Room, Json.obj()) {
      daoRunner.run(for {
        existing <- dao.findRoomsQuery.filter(_.id === roomID).result.head
        updated <- dao.update(
          StoredRoom(
            existing.id,
            room.buildingID,
            room.name,
            room.wai2GoID,
            room.available,
            existing.created,
            JavaTime.offsetDateTime
          ),
          version
        )
        building <- dao.findBuildingsQuery.filter(_.id === room.buildingID).map(_.building).result.head
      } yield (updated, building))
        .map { case (r, b) => Right(r.asRoom(b)) }
    }

  override def findBuilding(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Building]] =
    daoRunner.run(dao.findBuildingsQuery.filter(_.id === id).map(_.building).result.head)
      .map(Right.apply)
      .recover {
        case _: NoSuchElementException => ServiceResults.error[Building](s"Could not find a Building with ID $id")
      }

  override def findRoom(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Room]] =
    daoRunner.run(dao.findRoomsQuery.filter(_.id === id).withBuilding.mapToRoom.result.head)
      .map(Right.apply)
      .recover {
        case _: NoSuchElementException => ServiceResults.error[Room](s"Could not find a Room with ID $id")
      }

  override def allBuildings(implicit t: TimingContext): Future[ServiceResult[Seq[Building]]] =
    daoRunner.run(dao.findBuildingsQuery.sortBy(_.name).map(_.building).result)
      .map(Right.apply)

  override def allRooms(implicit t: TimingContext): Future[ServiceResult[Seq[Room]]] =
    daoRunner.run(
      dao.findRoomsQuery
        .withBuilding
        .sortBy { case (r, b) => (b.name, r.name) }
        .mapToRoom
        .result
    ).map(Right.apply)

  override def availableRooms(implicit t: TimingContext): Future[ServiceResult[Seq[Room]]] =
    daoRunner.run(
      dao.findRoomsQuery
        .withBuilding
        .filter { case (r, _) => r.available }
        .sortBy { case (r, b) => (b.name, r.name) }
        .mapToRoom
        .result
    ).map(Right.apply)

}