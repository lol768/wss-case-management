package controllers.admin

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import controllers.BaseController
import controllers.admin.AppointmentController._
import controllers.refiners._
import domain._
import helpers.{FormHelpers, JavaTime}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.tabula.ProfileService
import services.{AppointmentService, CaseService, PermissionService}
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object AppointmentController {
  case class AppointmentFormData(
    clients: Set[UniversityID],
    caseID: Option[UUID],
    appointment: AppointmentSave,
    version: Option[OffsetDateTime]
  )

  def form(team: Team, profileService: ProfileService, caseService: CaseService, existingVersion: Option[OffsetDateTime] = None)(implicit t: TimingContext, executionContext: ExecutionContext): Form[AppointmentFormData] = {
    // TODO If we're linked to a case, is it valid to have an appointment with someone who isn't a client on the case?
    def isValid(u: UniversityID): Boolean =
      Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
        .toOption.exists(_.isRight)

    def isValidCase(id: UUID): Boolean =
      Try(Await.result(caseService.find(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(
      mapping(
        "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u))).verifying("error.required", _.exists(_.string.nonEmpty)),
        "case" -> optional(uuid.verifying("error.required", id => isValidCase(id))),
        "appointment" -> mapping(
          "subject" -> nonEmptyText(maxLength = Appointment.SubjectMaxLength),
          "start" -> FormHelpers.offsetDateTime.verifying("error.appointment.start.inPast", _.isAfter(JavaTime.offsetDateTime)),
          "duration" -> number(min = 60, max = 120 * 60).transform[Duration](n => Duration.ofSeconds(n.toLong), _.getSeconds.toInt),
          "location" -> optional(Location.formField),
          "teamMember" -> nonEmptyText.transform[Usercode](Usercode.apply, _.string), // TODO validate
          "appointmentType" -> AppointmentType.formField
        )(AppointmentSave.apply)(AppointmentSave.unapply),
        "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
      )(AppointmentFormData.apply)(AppointmentFormData.unapply)
    )
  }
}

@Singleton
class AppointmentController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  appointments: AppointmentService,
  userLookupService: UserLookupService,
  permissions: PermissionService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  appointmentActionFilters: AppointmentActionFilters,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import appointmentActionFilters._
  import canViewTeamActionRefiner._

  private def renderAppointment(appointmentKey: IssueKey)(implicit request: AppointmentSpecificRequest[AnyContent]): Future[Result] =
    appointments.findForRender(appointmentKey).successFlatMap { render =>
      profiles.getProfiles(render.clients.map(_.universityID)).successMap { clientProfiles =>
        val usercodes = Seq(render.appointment.teamMember)
        val userLookup = userLookupService.getUsers(usercodes).toOption.getOrElse(Map())

        val clients =
          render.clients.toSeq
            .map { client =>
              client ->
                clientProfiles.get(client.universityID)
                  .fold[Either[UniversityID, SitsProfile]](Left(client.universityID))(Right.apply)
            }
            .sortBy { case (_, e) => (e.isLeft, e.right.map(_.fullName).toOption) }

        Ok(views.html.admin.appointments.view(
          render,
          clients,
          userLookup
        ))
      }
    }

  def view(appointmentKey: IssueKey): Action[AnyContent] = CanViewAppointmentAction(appointmentKey).async { implicit appointmentRequest =>
    renderAppointment(appointmentKey)
  }

  def createSelectTeam(forCase: Option[IssueKey], client: Option[UniversityID]): Action[AnyContent] = AnyTeamMemberRequiredAction { implicit request =>
    permissions.teams(request.context.user.get.usercode).fold(showErrors, teams => {
      if (teams.size == 1)
        Redirect(controllers.admin.routes.AppointmentController.createForm(teams.head.id, forCase, client))
      else
        Ok(views.html.admin.appointments.createSelectTeam(teams, forCase, client))
    })
  }

  def createForm(teamId: String, forCase: Option[IssueKey], client: Option[UniversityID]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val baseForm = form(teamRequest.team, profiles, cases)
    val baseBind = Map("appointment.teamMember" -> teamRequest.context.user.get.usercode.string)

    (forCase, client) match {
      case (Some(_), Some(_)) => Future.successful(
        BadRequest("Can't specify both forCase and client")
      )

      case (Some(caseKey), _) => cases.find(caseKey).successFlatMap { clientCase =>
        cases.getClients(clientCase.id.get).successMap { clients =>
          Ok(views.html.admin.appointments.create(
            teamRequest.team,
            baseForm.bind(baseBind ++ Map(
              "case" -> clientCase.id.get.toString
            ) ++ clients.toSeq.zipWithIndex.map { case (universityID, index) =>
              s"clients[$index]" -> universityID.string
            }.toMap).discardingErrors,
            Some(clientCase)
          ))
        }
      }

      case (_, Some(universityID)) => Future.successful(
        Ok(views.html.admin.appointments.create(
          teamRequest.team,
          baseForm.bind(baseBind ++ Map(
            "clients[0]" -> universityID.string
          )).discardingErrors,
          None
        ))
      )

      case _ => Future.successful(
        Ok(views.html.admin.appointments.create(teamRequest.team, baseForm.bind(baseBind).discardingErrors, None))
      )
    }
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles, cases).bindFromRequest().fold(
      formWithErrors => formWithErrors.data.get("case") match {
        case Some(caseID) =>
          cases.find(UUID.fromString(caseID)).successMap { clientCase =>
            Ok(views.html.admin.appointments.create(teamRequest.team, formWithErrors, Some(clientCase)))
          }

        case None =>
          Future.successful(
            Ok(views.html.admin.appointments.create(teamRequest.team, formWithErrors, None))
          )
      },
      data => {
        val clients = data.clients.filter(_.string.nonEmpty)

        appointments.create(data.appointment, clients, teamRequest.team, data.caseID).successMap { appointment =>
          Redirect(controllers.admin.routes.AppointmentController.view(appointment.key))
            .flashing("success" -> Messages("flash.appointment.created", appointment.key.string))
        }
      }
    )
  }

  def editForm(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    appointments.findForRender(appointmentKey).successMap { a =>
      Ok(
        views.html.admin.appointments.edit(
          a.appointment,
          form(a.appointment.team, profiles, cases, Some(a.appointment.lastUpdated))
            .fill(AppointmentFormData(
              a.clients.map(_.universityID),
              a.clientCase.flatMap(_.id),
              AppointmentSave(
                a.appointment.subject,
                a.appointment.start,
                a.appointment.duration,
                a.appointment.location,
                a.appointment.teamMember,
                a.appointment.appointmentType
              ),
              Some(a.appointment.lastUpdated)
            )),
          a.clientCase
        )
      )
    }
  }

  def edit(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    appointments.findForRender(appointmentKey).successFlatMap { a =>
      form(a.appointment.team, profiles, cases, Some(a.appointment.lastUpdated)).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(
            views.html.admin.appointments.edit(
              a.appointment,
              formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", a.appointment.lastUpdated)),
              a.clientCase
            )
          )
        ),
        data => {
          val clients = data.clients.filter(_.string.nonEmpty)

          appointments.update(a.appointment.id, data.appointment, clients, data.version.get).successMap { updated =>
            Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
              .flashing("success" -> Messages("flash.appointment.updated"))
          }
        }
      )
    }
  }

}
