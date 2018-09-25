package controllers

import controllers.API.Response._
import controllers.CaseSearchController._
import controllers.refiners.{AnyTeamActionRefiner, CanViewCaseActionRefiner}
import domain.dao.CaseDao.{Case, CaseSearchQuery}
import domain.{CaseType, IssueKey, IssueStateFilter, Teams}
import helpers.JavaTime
import helpers.Json.JsonClientError
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, PermissionService}
import warwick.sso.{AuthenticatedRequest, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object CaseSearchController {
  val form = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "team" -> optional(Teams.formField),
    "member" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
    "caseType" -> optional(CaseType.formField),
    "state" -> optional(IssueStateFilter.formField)
  )(CaseSearchQuery.apply)(CaseSearchQuery.unapply))
}

@Singleton
class CaseSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  viewCaseActionRefiner: CanViewCaseActionRefiner,
  cases: CaseService,
  permissions: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import viewCaseActionRefiner._

  def canDoQuery(query: CaseSearchQuery)(implicit request: AuthenticatedRequest[_]): Future[ServiceResult[Boolean]] = {
    if (query.team.isEmpty && query.member.isEmpty) {
      permissions.isAdmin(currentUser.usercode)
    } else if (query.team.nonEmpty) {
      permissions.canViewTeamFuture(currentUser.usercode, query.team.get)
    } else {
      Future.successful(Right(currentUser.usercode == query.member.get))
    }
  }
  def search: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(
        BadRequest(Json.toJson(API.Failure[JsValue](
          status = "bad-request",
          errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
        )))
      ),
      query => {
        canDoQuery(query).successFlatMap { canDo =>
          if (!canDo) {
            Future.successful(Forbidden(Json.toJson(JsonClientError(status = "forbidden", errors = Seq(s"User ${currentUser.usercode.string} does not have permission to run this query")))))
          } else {
            val (category: String, results: Future[ServiceResult[Seq[Case]]]) =
              if (query.isEmpty) "Recently viewed cases" -> cases.findRecentlyViewed(request.user.get.usercode, 10)
              else "Search results" -> cases.search(query, 10)

            results.successMap { c =>
              Ok(Json.toJson(API.Success(data = Json.obj(
                "results" -> c.map(toJson(_, Some(category)))
              ))))
            }
          }
        }
      }
    )
  }

  def lookup(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey) { implicit caseRequest =>
    Ok(Json.toJson(API.Success(data = Json.obj(
      "results" -> Seq(toJson(caseRequest.`case`))
    ))))
  }

  private def toJson(c: Case, category: Option[String] = None): JsObject = Json.obj(
    "id" -> c.id.get,
    "key" -> c.key.get.string,
    "subject" -> c.subject,
    "team" -> c.team.name,
    "caseType" -> c.caseType.map(_.description),
    "created" -> c.created.format(JavaTime.iSO8601DateFormat),
    "state" -> c.state.entryName,
    "category" -> category
  )

}
