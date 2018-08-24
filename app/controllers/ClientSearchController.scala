package controllers

import helpers.ServiceResults.ServiceError
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.tabula.MemberSearchService
import services.tabula.TabulaResponseParsers.MemberSearchResult
import services.{ClientSummaryService, SecurityService}
import warwick.sso.{User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientSearchController @Inject()(
  securityService: SecurityService,
  userLookupService: UserLookupService,
  memberSearchService: MemberSearchService,
  clientSummaryService: ClientSummaryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  def search(query: String): Action[AnyContent] = securityService.SigninRequiredAction.async { implicit request =>
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
          userLookupService.getUser(Usercode(query)).fold(
            e => Future.successful(Left(List(new ServiceError {
              override def message: String = e.getMessage
              override def cause = Some(e)
            }))),
            user => if (user.universityId.nonEmpty)
              Future.successful(Right(user))
            else
              Future.successful(Left(List(new ServiceError {
                override def message: String = s"Found user matching $query but user had no University ID"
              })))
          ).map(_.fold(
            _ => Ok(Json.toJson(API.Success(data = Json.obj()))), // Don't care if this fails
            user => Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> Json.arr(toJson(user))
            ))))
          ))
        }
      }
    }

  }

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
