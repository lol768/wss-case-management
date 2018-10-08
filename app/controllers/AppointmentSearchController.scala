package controllers

import controllers.API.Response._
import controllers.AppointmentSearchController._
import controllers.refiners.{AnyTeamActionRefiner, AppointmentActionFilters}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import helpers.{JavaTime, ServiceResults}
import helpers.Json.JsonClientError
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import services.tabula.ProfileService
import services.{AppointmentService, PermissionService}
import warwick.sso.{AuthenticatedRequest, UniversityID, User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object AppointmentSearchController {
  val form = Form(mapping(
    "query" -> optional(text),
    "createdAfter" -> optional(localDate),
    "createdBefore" -> optional(localDate),
    "startAfter" -> optional(localDate),
    "startBefore" -> optional(localDate),
    "location" -> optional(Location.formField),
    "team" -> optional(Teams.formField),
    "member" -> optional(nonEmptyText).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string)),
    "appointmentType" -> optional(AppointmentType.formField)
  )(AppointmentSearchQuery.apply)(AppointmentSearchQuery.unapply))
}

@Singleton
class AppointmentSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  appointmentActionFilters: AppointmentActionFilters,
  appointmentService: AppointmentService,
  permissions: PermissionService,
  profileService: ProfileService,
  userLookupService: UserLookupService
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
              ServiceResults.futureSequence(
                appointments.map(a => appointmentService.getClients(a.id).map(_.map(c => a -> c)))
              ).successFlatMap { appointmentsAndClients =>
                val universityIDs = appointmentsAndClients.flatMap { case (_, c) => c.map(_.universityID) }
                profileService.getProfiles(universityIDs.toSet).successMap { profiles =>
                  val userLookup = userLookupService.getUsers(appointments.map(_.teamMember)).toOption.getOrElse(Map())
                  Ok(Json.toJson(API.Success(data = Json.obj(
                    "results" -> appointmentsAndClients.map { case (a, c) => toJson(
                      a,
                      userLookup,
                      c.map(c => profiles.get(c.universityID).map(Right.apply).getOrElse(Left(c.universityID))),
                      Some(category)
                    )}
                  ))))
                }
              }
            }
          }
        }
      }
    )
  }

  def lookup(appointmentKey: IssueKey): Action[AnyContent] = CanViewAppointmentAction(appointmentKey).async { implicit request =>
    appointmentService.getClients(request.appointment.id).successFlatMap(clients =>
      profileService.getProfiles(clients.map(_.universityID)).successMap(profiles =>
        Ok(Json.toJson(API.Success(data = Json.obj(
          "results" -> Seq(toJson(
            request.appointment,
            userLookupService.getUser(request.appointment.teamMember).toOption.map(u => request.appointment.teamMember -> u).toMap,
            clients.map(c => profiles.get(c.universityID).map(Right.apply).getOrElse(Left(c.universityID))),
          ))
        ))))
      )
    )
  }

  private def toJson(
    a: Appointment,
    teamMemberUser: Map[Usercode, User],
    clients: Set[Either[UniversityID, SitsProfile]],
    category: Option[String] = None
  ): JsObject = Json.obj(
    "id" -> a.id,
    "key" -> a.key.string,
    "subject" -> a.subject(Some(teamMemberUser), Some(clients)),
    "team" -> a.team.name,
    "appointmentType" -> a.appointmentType.description,
    "created" -> a.created.format(JavaTime.iSO8601DateFormat),
    "start" -> a.start.format(JavaTime.iSO8601DateFormat),
    "state" -> a.state.entryName,
    "category" -> category
  )

}
