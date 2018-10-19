package controllers

import controllers.refiners.AnyTeamActionRefiner
import domain.Team
import helpers.ServiceResults
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services._
import warwick.sso.{User, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MemberSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  memberService: MemberService,
  permissionService: PermissionService,
  photoService: PhotoService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  def search(query: String): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    if (query.safeTrim.length < 3) {
      Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
    } else {
      memberService.search(query.safeTrim).map(_.map(_.take(10))).successMap { memberResults =>
        if (memberResults.nonEmpty) {
          // Inflate
          val users = userLookupService.getUsers(memberResults.map(_.usercode)).toOption.getOrElse(Map())
          ServiceResults.sequence(users.values.map(user =>
            permissionService.teams(user.usercode).map(memberTeams =>
              toJson(user, memberTeams)
            )
          ).toSeq).fold(
            showErrors,
            json => Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> json
            ))))
          )
        } else {
          Ok(Json.toJson(API.Success(data = Json.obj())))
        }
      }
    }
  }

  private def toJson(user: User, teams: Seq[Team]) = Json.obj(
    "name" -> user.name.full,
    "team" -> teams.map(_.name).mkString(", "),
    "value" -> user.usercode.string,
    "photo" -> photoService.photoUrl(user.universityId.get)
  )

}
