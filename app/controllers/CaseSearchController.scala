package controllers

import controllers.API.Response._
import controllers.CaseSearchController._
import controllers.refiners.{AnyTeamActionRefiner, CanViewCaseActionRefiner, PermissionsFilter}
import domain.dao.CaseDao.{Case, CaseSearchQuery}
import domain.{CaseType, IssueKey, IssueStateFilter, Teams}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, ActionFilter, AnyContent}
import services.{CaseService, PermissionService}
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

object CaseSearchController {
  val form = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "team" -> optional(Teams.formField),
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

  private val canViewAnyCase: ActionFilter[AuthenticatedRequest] = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissions.canViewCase(request.context.user.get.usercode)
  }

  def search: Action[AnyContent] = (AnyTeamMemberRequiredAction andThen canViewAnyCase).async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(
        BadRequest(Json.toJson(API.Failure[JsValue](
          status = "bad-request",
          errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
        )))
      ),
      query => {
        val results: Future[ServiceResult[Seq[Case]]] =
          if (query.isEmpty) cases.findRecentlyViewed(request.user.get.usercode, 10)
          else cases.search(query, 10)

        results.successMap { c =>
          Ok(Json.toJson(API.Success(data = Json.obj(
            "results" -> c.map(toJson)
          ))))
        }
      }
    )
  }

  def lookup(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey) { implicit caseRequest =>
    Ok(Json.toJson(API.Success(data = Json.obj(
      "results" -> Seq(toJson(caseRequest.`case`))
    ))))
  }

  private def toJson(c: Case): JsObject = Json.obj(
    "id" -> c.id.get,
    "key" -> c.key.get.string,
    "subject" -> c.subject,
    "team" -> c.team.name,
    "caseType" -> c.caseType.map(_.description),
    "created" -> c.created.format(JavaTime.iSO8601DateFormat),
    "state" -> c.state.entryName
  )

}
