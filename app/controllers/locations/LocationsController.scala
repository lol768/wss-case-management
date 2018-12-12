package controllers.locations

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.locations.LocationsController._
import controllers.refiners.AdminActionRefiner
import domain.{Building, BuildingSave, Room, RoomSave}
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.LocationService
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.Usercode

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object LocationsController {
  case class BuildingFormData(
    building: BuildingSave,
    version: Option[OffsetDateTime]
  )

  def buildingForm(existingVersion: Option[OffsetDateTime] = None): Form[BuildingFormData] =
    Form(mapping(
      "building" -> mapping(
        "name" -> nonEmptyText(maxLength = Building.NameMaxLength),
        "wai2GoID" -> number
      )(BuildingSave.apply)(BuildingSave.unapply),
      "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
    )(BuildingFormData.apply)(BuildingFormData.unapply))

  case class RoomFormData(
    room: RoomSave,
    version: Option[OffsetDateTime]
  )

  def roomForm(locations: LocationService, existingVersion: Option[OffsetDateTime] = None)(implicit t: TimingContext, executionContext: ExecutionContext): Form[RoomFormData] = {
    def isValidBuilding(id: UUID): Boolean =
      Try(Await.result(locations.findBuilding(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(mapping(
      "room" -> mapping(
        "building" -> uuid.verifying("error.required", id => isValidBuilding(id)),
        "name" -> nonEmptyText(maxLength = Room.NameMaxLength),
        "wai2GoID" -> optional(number),
        "available" -> boolean,
        "o365Usercode" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
      )(RoomSave.apply)(RoomSave.unapply),
      "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
    )(RoomFormData.apply)(RoomFormData.unapply))
  }
}

@Singleton
class LocationsController @Inject()(
  locations: LocationService,
  adminActionRefiner: AdminActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import adminActionRefiner._

  def list(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    ServiceResults.zip(
      locations.allBuildings,
      locations.allRooms
    ).successMap { case (buildings, rooms) =>
      Ok(views.html.locations.list(buildings, rooms))
    }
  }

  def createBuildingForm(): Action[AnyContent] = AdminRequiredAction { implicit request =>
    Ok(views.html.locations.createBuilding(buildingForm()))
  }

  def createBuilding(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    buildingForm().bindFromRequest.fold(
      formWithErrors => Future.successful(
        Ok(views.html.locations.createBuilding(formWithErrors))
      ),
      data => locations.create(data.building).successMap { _ =>
        Redirect(controllers.locations.routes.LocationsController.list())
          .flashing("success" -> Messages("flash.building.created"))
      }
    )
  }

  def editBuildingForm(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    locations.findBuilding(id).successMap { building =>
      Ok(views.html.locations.editBuilding(
        building,
        buildingForm(Some(building.lastUpdated))
          .fill(
            BuildingFormData(
              BuildingSave(building.name, building.wai2GoID),
              Some(building.lastUpdated)
            )
          )
      ))
    }
  }

  def editBuilding(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    locations.findBuilding(id).successFlatMap { building =>
      buildingForm(Some(building.lastUpdated)).bindFromRequest.fold(
        formWithErrors => Future.successful(
          Ok(views.html.locations.editBuilding(building, formWithErrors))
        ),
        data => locations.update(id, data.building, data.version.get).successMap { _ =>
          Redirect(controllers.locations.routes.LocationsController.list())
            .flashing("success" -> Messages("flash.building.updated"))
        }
      )
    }
  }

  def createRoomForm(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    locations.allBuildings.successMap { buildings =>
      Ok(views.html.locations.createRoom(buildings, roomForm(locations)))
    }
  }

  def createRoom(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    locations.allBuildings.successFlatMap { buildings =>
      roomForm(locations).bindFromRequest.fold(
        formWithErrors => Future.successful(
          Ok(views.html.locations.createRoom(buildings, formWithErrors))
        ),
        data => locations.create(data.room).successMap { _ =>
          Redirect(controllers.locations.routes.LocationsController.list())
            .flashing("success" -> Messages("flash.room.created"))
        }
      )
    }
  }

  def editRoomForm(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    ServiceResults.zip(
      locations.findRoom(id),
      locations.allBuildings,
    ).successMap { case (room, buildings) =>
      Ok(views.html.locations.editRoom(
        room,
        buildings,
        roomForm(locations, Some(room.lastUpdated))
          .fill(
            RoomFormData(
              RoomSave(room.building.id, room.name, Some(room.wai2GoID).filterNot(_ == room.building.wai2GoID), room.available, room.o365Usercode),
              Some(room.lastUpdated)
            )
          )
      ))
    }
  }

  def editRoom(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    ServiceResults.zip(
      locations.findRoom(id),
      locations.allBuildings
    ).successFlatMap { case (room, buildings) =>
      roomForm(locations, Some(room.lastUpdated)).bindFromRequest.fold(
        formWithErrors => Future.successful(
          Ok(views.html.locations.editRoom(room, buildings, formWithErrors))
        ),
        data => locations.update(id, data.room, data.version.get).successMap { _ =>
          Redirect(controllers.locations.routes.LocationsController.list())
            .flashing("success" -> Messages("flash.room.updated"))
        }
      )
    }
  }

}
