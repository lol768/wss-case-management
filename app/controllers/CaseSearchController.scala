package controllers

import controllers.refiners.AnyTeamActionRefiner
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, PermissionService}
import CaseSearchController._
import play.api.libs.json.Json

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

  def search: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    form.bindFromRequest().fold(
      _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj())))),
      query => {
        ???
      }
    )
  }

}
