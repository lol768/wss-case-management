package controllers

import controllers.API.Response._
import controllers.AppointmentSearchController._
import controllers.refiners.{AnyTeamActionRefiner, AppointmentActionFilters}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import helpers.FormHelpers
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import services.{AppointmentService, PermissionService}
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.sso.{AuthenticatedRequest, UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object AppointmentSearchController {
  val form: Form[AppointmentSearchQuery] = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "client" -> optional(nonEmptyText.transform[UniversityID](UniversityID.apply, _.string)),
    "startAfter" -> optional(localDate),
    "startBefore" -> optional(localDate),
    "endAfter" -> optional(FormHelpers.offsetDateTime),
    "endBefore" -> optional(FormHelpers.offsetDateTime),
    "roomID" -> optional(uuid),
    "team" -> optional(Teams.formField),
    "member" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
    "appointmentType" -> optional(AppointmentType.formField),
    "purpose" -> optional(AppointmentPurpose.formField),
    "hasOutcome" -> optional(boolean),
    "states" -> set(AppointmentState.formField),
  )(AppointmentSearchQuery.apply)(AppointmentSearchQuery.unapply))
}

@Singleton
class AppointmentSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  appointmentActionFilters: AppointmentActionFilters,
  appointmentService: AppointmentService,
  permissions: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import appointmentActionFilters._

  def canDoQuery(query: AppointmentSearchQuery)(implicit request: AuthenticatedRequest[_]): Future[ServiceResult[Boolean]] = {
    if (query.team.isEmpty && query.teamMember.isEmpty) {
      permissions.isAdmin(currentUser.usercode)
    } else if (query.team.nonEmpty) {
      permissions.canViewTeamFuture(currentUser.usercode, query.team.get)
    } else {
      Future.successful(Right(currentUser.usercode == query.teamMember.get))
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
            val (category: String, results: Future[ServiceResult[Seq[Appointment]]]) =
              if (query.isEmpty) "Recently viewed appointments" -> appointmentService.findRecentlyViewed(request.user.get.usercode, 10)
              else "Search results" -> appointmentService.search(query, 10)

            results.successFlatMap { appointments =>
              ServiceResults.zip(
                ServiceResults.futureSequence(
                  appointments.map(a => appointmentService.getClients(a.id).map(_.map(c => a -> c)))
                ),
                ServiceResults.futureSequence(
                  appointments.map(a => appointmentService.getTeamMembers(a.id).map(_.map(tm => a -> tm)))
                )
              ).successMap { case (appointmentsAndClients, appointmentsAndTeamMembers) =>
                Ok(Json.toJson(API.Success(data = Json.obj(
                  "results" -> appointmentsAndClients.map { case (a, c) => toJson(
                    a,
                    c,
                    appointmentsAndTeamMembers.toMap.apply(a),
                    Some(category)
                  )}
                ))))
              }
            }
          }
        }
      }
    )
  }

  def lookup(appointmentKey: IssueKey): Action[AnyContent] = CanViewAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(
      appointmentService.getClients(request.appointment.id),
      appointmentService.getTeamMembers(request.appointment.id)
    ).successMap { case (clients, teamMembers) =>
      Ok(Json.toJson(API.Success(data = Json.obj(
        "results" -> Seq(toJson(
          request.appointment,
          clients,
          teamMembers,
        ))
      ))))
    }
  }

  private def toJson(
    a: Appointment,
    clients: Set[AppointmentClient],
    teamMembers: Set[AppointmentTeamMember],
    category: Option[String] = None
  ): JsObject = Json.obj(
    "id" -> a.id,
    "key" -> a.key.string,
    "subject" -> a.subject(Some(clients), Some(teamMembers)),
    "team" -> a.team.name,
    "appointmentType" -> a.appointmentType.description,
    "purpose" -> a.purpose.description,
    "created" -> a.created.format(JavaTime.iSO8601DateFormat),
    "start" -> a.start.format(JavaTime.iSO8601DateFormat),
    "state" -> a.state.entryName,
    "category" -> category
  )

}
