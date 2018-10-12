package controllers

import controllers.refiners.AnyTeamActionRefiner
import domain.{Client, SitsProfile}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
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
      Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
    } else {
      // Check client table
      clientService.search(query.safeTrim).map(_.map(_.take(10))).successFlatMap { clientResults =>
        if (clientResults.nonEmpty) {
          // Inflate
          profileService.getProfiles(clientResults.map(_.universityID).toSet).successFlatMap { profileMap =>
            Future.successful(Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> clientResults.map(c => toJson(c, profileMap.get(c.universityID)))
            )))))
          }
        } else {
          // Search for alternate email
          clientSummaryService.getByAlternativeEmailAddress(query).successFlatMap {
            case Some(summary) =>
              // Inflate
              profileService.getProfile(summary.client.universityID).map(_.value).successMap { profileOption =>
                Ok(Json.toJson(API.Success(data = Json.obj(
                  "results" -> toJson(summary.client, profileOption)
                ))))
              }
            case _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
          }
        }
      }
    }
  }

  private def toJson(client: Client, profile: Option[SitsProfile]) = Json.obj(
    "name" -> client.safeFullName,
    "department" -> profile.map(_.department.name),
    "userType" -> profile.map(_.userType.entryName),
    "type" -> "user",
    "value" -> client.universityID.string,
    "photo" -> profile.flatMap(_.photo)
  )

}
