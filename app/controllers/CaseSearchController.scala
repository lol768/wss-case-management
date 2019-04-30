package controllers

import controllers.API.Response._
import controllers.CaseSearchController._
import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.CaseDao.CaseSearchQuery
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, PermissionService}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

object CaseSearchController {
  val form = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "team" -> optional(Teams.formField),
    "member" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
    "caseType" -> optional(CaseType.formField),
    "state" -> optional(IssueStateFilter.formField),
  )(CaseSearchQuery.apply)(CaseSearchQuery.unapply))
}

@Singleton
class CaseSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  permissions: PermissionService
)(implicit
  cases: CaseService,
  executionContext: ExecutionContext
) extends BaseController {

  import anyTeamActionRefiner._

  def search: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(
        BadRequest(Json.toJson(API.Failure[JsValue](
          status = "bad-request",
          errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
        )))
      ),
      query => {
        val (category: String, results: Future[ServiceResult[Seq[Case]]]) =
          if (query.isEmpty) "Recently viewed cases" -> cases.findRecentlyViewed(request.user.get.usercode, 10)
          else "Search results" -> cases.search(query, 10)

        results.successFlatMapTo( c =>
          ServiceResults.zip(results, cases.getClients(c.map(_.id).toSet))
        ).successMap { case (c, clients) =>
          Ok(Json.toJson(API.Success(data = Json.obj(
            "results" -> c.map(clientCase => toJson(clientCase, clients.getOrElse(clientCase.id, Set()), Some(category)))
          ))))
        }
      }
    )
  }

  def lookup(caseKeyorId: String): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(refiners.WithCase(caseKeyorId)).async { implicit caseRequest =>
    cases.getClients(caseRequest.`case`.id).successMap { clients =>
      Ok(Json.toJson(API.Success(data = Json.obj(
        "results" -> Seq(toJson(caseRequest.`case`, clients))
      ))))
    }
  }

  private def toJson(c: Case, clients: Set[Client], category: Option[String] = None): JsObject = Json.obj(
    "id" -> c.id,
    "key" -> c.key.string,
    "subject" -> c.subject,
    "team" -> c.team.name,
    "clients" -> clients.map(_.safeFullName).mkString(", "),
    "caseType" -> c.caseType.map(_.description),
    "created" -> c.created.format(JavaTime.iSO8601DateFormat),
    "state" -> c.state.entryName,
    "category" -> category,
    "url" -> controllers.admin.routes.CaseController.view(c.key).toString
  )

}
