package controllers

import controllers.refiners.AnyTeamActionRefiner
import helpers.ServiceResults
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.ClientSummaryService
import services.tabula.MemberSearchService
import services.tabula.TabulaResponseParsers.MemberSearchResult
import warwick.sso.{User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  userLookupService: UserLookupService,
  memberSearchService: MemberSearchService,
  clientSummaryService: ClientSummaryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  def search(query: String): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    if (query.safeTrim.length < 3) {
      Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
    } else {
      // Check Tabula first
      memberSearchService.search(query).successFlatMap { memberResults =>
        if (memberResults.nonEmpty) {
          Future.successful(Ok(Json.toJson(API.Success(data = Json.obj(
            "results" -> memberResults.map(toJson)
          )))))
        } else if (query.contains("@")) {
          // Search for alternate email
          clientSummaryService.getByAlternativeEmailAddress(query).successFlatMap {
            case Some(summary) => memberSearchService.search(summary.universityID.string).successMap(uniIdMembers =>
              Ok(Json.toJson(API.Success(data = Json.obj(
                "results" -> uniIdMembers.map(toJson)
              ))))
            )
            case _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
          }
        } else {
          // Check for usercode directly
          Future.successful(getUser(Usercode(query)).fold(
            _ => Ok(Json.toJson(API.Success(data = Json.obj()))), // Don't care if this fails
            user => Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> Json.arr(toJson(user))
            ))))
          ))

        }
      }
    }

  }

  private def getUser(usercode: Usercode): ServiceResult[User] =
    userLookupService.getUser(usercode).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      user => if (user.universityId.nonEmpty)
        Right(user)
      else
        Left(List(ServiceError(s"Found user matching $user but user had no University ID")))
    )

  private def toJson(member: MemberSearchResult) = Json.obj(
    "name" -> s"${member.firstName} ${member.lastName}",
    "department" -> member.department.name,
    "userType" -> member.userType,
    "type" -> "user",
    "value" -> member.universityID.string,
    "photo" -> member.photo
  )

  private def toJson(user: User) = Json.obj(
    "name" -> user.name.full.getOrElse[String]("[Unknown user]"),
    "department" -> user.department.flatMap(_.shortName).getOrElse[String]("[Unknown department]"),
    "userType" -> user.rawProperties.getOrElse[String]("urn:websignon:usertype", "[Unknown type]"),
    "type" -> "user",
    "value" -> user.universityId.map(_.string).get
  )

}
