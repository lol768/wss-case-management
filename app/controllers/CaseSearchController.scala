package controllers

import controllers.CaseSearchController._
import controllers.refiners.{AnyTeamActionRefiner, PermissionsFilter}
import domain.dao.CaseDao.Case
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, PermissionService}
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

object CaseSearchController {
  val form = Form(single(
    "query" -> nonEmptyText
  ))
}

@Singleton
class CaseSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  cases: CaseService,
  permissions: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  private def canViewAnyCase = PermissionsFilter[AuthenticatedRequest] { implicit request =>
    permissions.canViewCase(request.context.user.get.usercode)
  }

  def search: Action[AnyContent] = (AnyTeamMemberRequiredAction andThen canViewAnyCase).async { implicit request =>
    form.bindFromRequest().fold(
      _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj())))),
      query => cases.textSearch(query, 10).successMap { c =>
        Ok(Json.toJson(API.Success(data = Json.obj(
          "results" -> c.map(toJson)
        ))))
      }
    )
  }

  private def toJson(c: Case): JsObject = Json.obj(
    "id" -> c.id.get,
    "key" -> c.key.get.string,
    "subject" -> c.subject,
    "team" -> c.team.name,
    "created" -> c.created.format(JavaTime.iSO8601DateFormat),
    "state" -> c.state.entryName
  )

}
