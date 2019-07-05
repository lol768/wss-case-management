package controllers.api

import java.time.OffsetDateTime

import controllers.BaseController
import domain.ClientSummary
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.mvc.{Action, AnyContent}
import services.{ClientSummaryService, SecurityService}
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

object ClientReasonableAdjustmentsAPIController {
  def toJson(clientSummary: ClientSummary): JsObject =
    Json.obj(
      "universityID" -> clientSummary.client.universityID.string,
      "reasonableAdjustments" -> clientSummary.reasonableAdjustments.map { ra =>
        Json.obj(
          "id" -> ra.id,
          "description" -> ra.description,
        )
      },
      "notes" -> clientSummary.reasonableAdjustmentsNotes,
      "lastUpdated" -> Json.toJson(clientSummary.updatedDate)(JavaTime.offsetDateTimeISOWrites),
    )
}

@Singleton
class ClientReasonableAdjustmentsAPIController @Inject()(
  clientSummaryService: ClientSummaryService,
  securityService: SecurityService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  def reasonableAdjustments(universityID: UniversityID): Action[AnyContent] = RequireAPIRead.async { implicit request =>
    clientSummaryService.get(universityID).successMap { clientSummary =>
      Ok(clientSummary.map(ClientReasonableAdjustmentsAPIController.toJson).getOrElse(Json.obj(
        "universityID" -> universityID.string,
        "reasonableAdjustments" -> Json.arr(),
        "notes" -> JsNull,
        "lastUpdated" -> JsNull
      )))
    }
  }

  def reasonableAdjustmentsUpdatedSince(since: OffsetDateTime): Action[AnyContent] = RequireAPIRead.async { implicit request =>
    clientSummaryService.findUpdatedSince(since).successMap { clientSummaries =>
      Ok(Json.obj(
        "clients" -> clientSummaries.map(ClientReasonableAdjustmentsAPIController.toJson)
      ))
    }
  }

}
