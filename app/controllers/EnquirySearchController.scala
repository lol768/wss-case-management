package controllers

import controllers.API.Response._
import controllers.EnquirySearchController._
import controllers.refiners.{AnyTeamActionRefiner, CanViewEnquiryActionRefiner, PermissionsFilter}
import domain.Enquiry.EnquirySearchQuery
import domain.{Enquiry, IssueKey, IssueStateFilter, Teams}
import helpers.JavaTime
import helpers.Json.JsonClientError
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{localDate, mapping, nonEmptyText, optional, text}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, ActionFilter, AnyContent}
import services.{EnquiryService, PermissionService}
import warwick.sso.{AuthenticatedRequest, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object EnquirySearchController {
  val form = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "team" -> optional(Teams.formField),
    "member" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
    "state" -> optional(IssueStateFilter.formField)
  )(EnquirySearchQuery.apply)(EnquirySearchQuery.unapply))
}

@Singleton
class EnquirySearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  viewEnquiryActionRefiner: CanViewEnquiryActionRefiner,
  enquiries: EnquiryService,
  permissions: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import viewEnquiryActionRefiner._

  def canDoQuery(query: EnquirySearchQuery)(implicit request: AuthenticatedRequest[_]): Future[ServiceResult[Boolean]] = {
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
            val (category: String, results: Future[ServiceResult[Seq[Enquiry]]]) =
              if (query.isEmpty) "Recently viewed enquiries" -> enquiries.findRecentlyViewed(request.user.get.usercode, 10)
              else "Search results" -> enquiries.search(query, 10)

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

  def lookup(enquiryKey: IssueKey): Action[AnyContent] = CanViewEnquiryAction(enquiryKey) { implicit enquiryRequest =>
    Ok(Json.toJson(API.Success(data = Json.obj(
      "results" -> Seq(toJson(enquiryRequest.enquiry))
    ))))
  }

  private def toJson(e: Enquiry, category: Option[String] = None): JsObject = Json.obj(
    "id" -> e.id.get,
    "key" -> e.key.get.string,
    "subject" -> e.subject,
    "team" -> e.team.name,
    "created" -> e.created.format(JavaTime.iSO8601DateFormat),
    "state" -> e.state.entryName,
    "category" -> category
  )

}
