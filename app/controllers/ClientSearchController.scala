package controllers

import controllers.refiners.AnyTeamActionRefiner
import domain.SitsProfile
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent}
import services._
import services.tabula.ProfileService
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  userLookupService: UserLookupService,
  clientService: ClientService,
  profileService: ProfileService,
  clientSummaryService: ClientSummaryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  def search(query: String): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    if (query.safeTrim.length < 3) {
      Future.successful(Ok(Json.toJson(API.Success(data = JsObject.empty))))
    } else {
      // Check client table
      clientService.search(query.safeTrim).map(_.map(_.take(10))).successFlatMap { clientResults =>
        if (clientResults.nonEmpty) {
          // Inflate and filter
          profileService.getProfiles(clientResults.map(_.universityID).toSet).successFlatMap { profileMap =>
            Future.successful(Ok(Json.toJson(API.Success(data = Json.obj("results" -> profileMap.values.map(toJson))))))
          }
        } else {
          // Search for alternate email
          clientSummaryService.getByAlternativeEmailAddress(query).successFlatMap {
            case Some(summary) =>
              // Inflate and filter
              profileService.getProfile(summary.client.universityID).map(_.value).successMap { profileOption =>
                profileOption.map(profile =>
                  Ok(Json.toJson(API.Success(data = Json.obj("results" -> toJson(profile)))))
                ).getOrElse(
                  Ok(Json.toJson(API.Success(data = JsObject.empty)))
                )
              }
            case _ => Future.successful(Ok(Json.toJson(API.Success(data = JsObject.empty))))
          }
        }
      }
    }
  }

  def searchFromPath(query: String): Action[AnyContent] = search(query)

  private def toJson(profile: SitsProfile) = Json.obj(
    "name" -> profile.fullName,
    "department" -> profile.department.name,
    "userType" -> profile.userType.entryName,
    "type" -> "user",
    "value" -> profile.universityID.string,
    "photo" -> profile.photo
  )

}
