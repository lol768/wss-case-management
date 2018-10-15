package controllers.admin

import java.time.format.DateTimeFormatter
import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import controllers.BaseController
import controllers.admin.AppointmentController.{form, _}
import controllers.refiners._
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.{FormHelpers, JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.tabula.ProfileService
import services.{AppointmentService, CaseService, LocationService, PermissionService}
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object AppointmentController {
  case class AppointmentFormData(
    clients: Set[UniversityID],
    cases: Set[Option[UUID]],
    appointment: AppointmentSave,
    version: Option[OffsetDateTime]
  )

  def form(team: Team, profileService: ProfileService, caseService: CaseService, permissionService: PermissionService, locationService: LocationService, existingVersion: Option[OffsetDateTime] = None)(implicit t: TimingContext, executionContext: ExecutionContext): Form[AppointmentFormData] = {
    // TODO If we're linked to a case, is it valid to have an appointment with someone who isn't a client on the case?
    def isValid(u: UniversityID): Boolean =
      Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
        .toOption.exists(_.isRight)

    def isValidTeamMember(usercode: Usercode): Boolean =
      Try(Await.result(permissionService.inAnyTeam(usercode), 5.seconds))
        .toOption.exists {
          case Right(true) => true
          case _ => false
        }

    def isValidCase(id: UUID): Boolean =
      Try(Await.result(caseService.find(id), 5.seconds))
        .toOption.exists(_.isRight)

    def isValidRoom(id: UUID): Boolean =
      Try(Await.result(locationService.findRoom(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(
      mapping(
        "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u))).verifying("error.required", _.exists(_.string.nonEmpty)),
        "cases" -> set(optional(uuid.verifying("error.required", id => isValidCase(id)))),
        "appointment" -> mapping(
          "start" -> FormHelpers.offsetDateTime.verifying("error.appointment.start.inPast", _.isAfter(JavaTime.offsetDateTime)),
          "duration" -> number(min = 60, max = 120 * 60).transform[Duration](n => Duration.ofSeconds(n.toLong), _.getSeconds.toInt),
          "roomID" -> optional(uuid.verifying("error.required", id => isValidRoom(id))),
          "teamMember" -> nonEmptyText.transform[Usercode](Usercode.apply, _.string).verifying("error.appointment.teamMember.invalid", u => isValidTeamMember(u)),
          "appointmentType" -> AppointmentType.formField
        )(AppointmentSave.apply)(AppointmentSave.unapply),
        "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
      )(AppointmentFormData.apply)(AppointmentFormData.unapply)
    )
  }

  case class CancelAppointmentData(
    cancellationReason: AppointmentCancellationReason,
    version: OffsetDateTime
  )

  def cancelForm(existingVersion: OffsetDateTime): Form[CancelAppointmentData] =
    Form(
      mapping(
        "cancellationReason" -> AppointmentCancellationReason.formField,
        "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == existingVersion)
      )(CancelAppointmentData.apply)(CancelAppointmentData.unapply)
    )

  case class AppointmentNoteFormData(
    text: String,
    version: OffsetDateTime
  )

  def appointmentNoteForm(version: OffsetDateTime): Form[AppointmentNoteFormData] = Form(mapping(
    "text" -> nonEmptyText,
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  )(AppointmentNoteFormData.apply)(AppointmentNoteFormData.unapply))

  def appointmentNoteFormPrefilled(version: OffsetDateTime): Form[AppointmentNoteFormData] =
    appointmentNoteForm(version).fill(AppointmentNoteFormData("", version))

  def deleteForm(version: OffsetDateTime): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  ))

}

@Singleton
class AppointmentController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  appointments: AppointmentService,
  userLookupService: UserLookupService,
  permissions: PermissionService,
  locations: LocationService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  appointmentActionFilters: AppointmentActionFilters,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import appointmentActionFilters._
  import canViewTeamActionRefiner._

  private def renderAppointment(appointmentKey: IssueKey, appointmentNoteForm: Form[AppointmentNoteFormData], cancelForm: Form[CancelAppointmentData])(implicit request: AppointmentSpecificRequest[AnyContent]): Future[Result] =
    appointments.findForRender(appointmentKey).successFlatMap { render =>
      profiles.getProfiles(render.clients.map(_.client.universityID)).successMap { clientProfiles =>
        val usercodes = render.notes.map(_.teamMember) ++ Seq(render.appointment.teamMember)
        val userLookup = userLookupService.getUsers(usercodes).toOption.getOrElse(Map())

        val clients = render.clients.map(c => c -> clientProfiles.get(c.client.universityID)).toMap

        Ok(views.html.admin.appointments.view(
          render,
          clients,
          userLookup,
          appointmentNoteForm,
          cancelForm
        ))
      }
    }

  def view(appointmentKey: IssueKey): Action[AnyContent] = CanViewAppointmentAction(appointmentKey).async { implicit request =>
    renderAppointment(
      appointmentKey,
      appointmentNoteFormPrefilled(request.appointment.lastUpdated),
      cancelForm(request.appointment.lastUpdated).bindVersion(request.appointment.lastUpdated).discardingErrors
    )
  }

  def createSelectTeam(forCase: Option[IssueKey], client: Option[UniversityID], start: Option[OffsetDateTime], duration: Option[Duration]): Action[AnyContent] = AnyTeamMemberRequiredAction { implicit request =>
    permissions.teams(request.context.user.get.usercode).fold(showErrors, teams => {
      if (teams.size == 1)
        Redirect(controllers.admin.routes.AppointmentController.createForm(teams.head.id, forCase, client, start, duration))
      else
        Ok(views.html.admin.appointments.createSelectTeam(teams, forCase, client, start, duration))
    })
  }

  def createForm(teamId: String, forCase: Option[IssueKey], client: Option[UniversityID], start: Option[OffsetDateTime], duration: Option[Duration]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val baseForm = form(teamRequest.team, profiles, cases, permissions, locations)
    val baseBind = Map("appointment.teamMember" -> teamRequest.context.user.get.usercode.string)

    if (forCase.nonEmpty && client.nonEmpty) {
      Future.successful(
        BadRequest("Can't specify both forCase and client")
      )
    } else {
      val getCase: Future[ServiceResult[Option[Case]]] = forCase.map { caseKey =>
        cases.find(caseKey).map(_.right.map(Some.apply))
      }.getOrElse(Future.successful(Right(None)))

      val binds: Seq[Future[Map[String, String]]] = Seq(
        Some(Future.successful(baseBind)),
        forCase.map { caseKey =>
          cases.find(caseKey).flatMap(_.fold(
            _ => Future.successful(Map.empty[String, String]),
            clientCase => cases.getClients(clientCase.id.get).map(_.fold(
              _ => Map("cases[0]" -> clientCase.id.get.toString),
              clients => Map(
                "cases[0]" -> clientCase.id.get.toString
              ) ++ clients.toSeq.zipWithIndex.map { case (c, index) =>
                s"clients[$index]" -> c.universityID.string
              }.toMap
            ))
          ))
        },
        client.map { universityID => Future.successful(Map("clients[0]" -> universityID.string)) },
        start.map { dt => Future.successful(Map("appointment.start" -> dt.toLocalDateTime.format(DateTimeFormatter.ofPattern(FormHelpers.Html5LocalDateTimePattern)))) },
        duration.map { dur => Future.successful(Map("appointment.duration" -> dur.getSeconds.toString)) }
      ).flatten

      Future.sequence(binds).map(_.reduce(_ ++ _)).flatMap { bind =>
        ServiceResults.zip(getCase, locations.availableRooms).successMap { case (clientCase, availableRooms) =>
          Ok(views.html.admin.appointments.create(
            teamRequest.team,
            baseForm.bind(bind).discardingErrors,
            clientCase.toSet,
            availableRooms
          ))
        }
      }
    }
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles, cases, permissions, locations).bindFromRequest().fold(
      formWithErrors => formWithErrors.data.keys.filter(_.startsWith("cases")).toSeq match {
        case caseKeys if caseKeys.nonEmpty =>
          val ids = caseKeys.map(k => formWithErrors.data(k)).flatMap(id => Try(UUID.fromString(id)).toOption).toSet
          ServiceResults.zip(cases.findAll(ids), locations.availableRooms).successMap { case (c, availableRooms) =>
            Ok(views.html.admin.appointments.create(teamRequest.team, formWithErrors, c.toSet, availableRooms))
          }

        case _ =>
          locations.availableRooms.successMap { availableRooms =>
            Ok (views.html.admin.appointments.create (teamRequest.team, formWithErrors, Set.empty, availableRooms) )
          }
        },
      data => {
        val clients = data.clients.filter(_.string.nonEmpty)
        val cases = data.cases.flatten.filter(_.toString.nonEmpty)

        appointments.create(data.appointment, clients, teamRequest.team, cases).successMap { appointment =>
          Redirect(controllers.admin.routes.AppointmentController.view(appointment.key))
            .flashing("success" -> Messages("flash.appointment.created", appointment.key.string))
        }
      }
    )
  }

  def editForm(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successMap { case (a, availableRooms) =>
      Ok(
        views.html.admin.appointments.edit(
          a.appointment,
          form(a.appointment.team, profiles, cases, permissions, locations, Some(a.appointment.lastUpdated))
            .fill(AppointmentFormData(
              a.clients.map(_.client.universityID),
              a.clientCases.flatMap(c => Some(c.id)),
              AppointmentSave(
                a.appointment.start,
                a.appointment.duration,
                a.room.map(_.id),
                a.appointment.teamMember,
                a.appointment.appointmentType
              ),
              Some(a.appointment.lastUpdated)
            )),
          a.clientCases,
          availableRooms
        )
      )
    }
  }

  def edit(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successFlatMap { case (a, availableRooms) =>
      form(a.appointment.team, profiles, cases, permissions, locations, Some(a.appointment.lastUpdated)).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(
            views.html.admin.appointments.edit(
              a.appointment,
              formWithErrors.bindVersion(a.appointment.lastUpdated),
              a.clientCases,
              availableRooms
            )
          )
        ),
        data => {
          val clients = data.clients.filter(_.string.nonEmpty)
          val cases = data.cases.flatten

          appointments.update(a.appointment.id, data.appointment, cases, clients, data.version.get).successMap { updated =>
            Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
              .flashing("success" -> Messages("flash.appointment.updated"))
          }
        }
      )
    }
  }

  def cancel(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    val appointment = request.appointment

    cancelForm(appointment.lastUpdated).bindFromRequest().fold(
      formWithErrors => renderAppointment(
        appointmentKey,
        appointmentNoteFormPrefilled(appointment.lastUpdated),
        formWithErrors.bindVersion(appointment.lastUpdated)
      ),
      data => appointments.cancel(appointment.id, data.cancellationReason, data.version).successMap { updated =>
        Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
          .flashing("success" -> Messages("flash.appointment.cancelled"))
      }
    )
  }

  def addNote(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    val appointment = request.appointment

    appointmentNoteForm(appointment.lastUpdated).bindFromRequest().fold(
      formWithErrors => renderAppointment(
        appointmentKey,
        formWithErrors.bindVersion(appointment.lastUpdated),
        cancelForm(appointment.lastUpdated).bindVersion(appointment.lastUpdated).discardingErrors
      ),
      data =>
        // We don't do anything with data.version here, it's validated but we don't lock the appointment when adding a note
        appointments.addNote(appointment.id, AppointmentNoteSave(data.text, currentUser.usercode)).successMap { _ =>
          Redirect(controllers.admin.routes.AppointmentController.view(appointmentKey))
            .flashing("success" -> Messages("flash.appointment.noteAdded"))
        }
    )
  }

  def editNoteForm(appointmentKey: IssueKey, id: UUID): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    withAppointmentNote(id) { note =>
      Future.successful(
        Ok(
          views.html.admin.appointments.editNote(
            appointmentKey,
            note,
            appointmentNoteForm(note.lastUpdated).fill(AppointmentNoteFormData(note.text, note.lastUpdated))
          )
        )
      )
    }
  }

  def editNote(appointmentKey: IssueKey, id: UUID): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    withAppointmentNote(id) { note =>
      appointmentNoteForm(note.lastUpdated).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(
            views.html.admin.appointments.editNote(
              appointmentKey,
              note,
              formWithErrors.bindVersion(note.lastUpdated)
            )
          )
        ),
        data =>
          appointments.updateNote(request.appointment.id, note.id, AppointmentNoteSave(data.text, currentUser.usercode), data.version).successMap { _ =>
            Redirect(controllers.admin.routes.AppointmentController.view(appointmentKey))
              .flashing("success" -> Messages("flash.appointment.noteUpdated"))
          }
      )
    }
  }

  def deleteNote(appointmentKey: IssueKey, id: UUID): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    withAppointmentNote(id) { note =>
      deleteForm(note.lastUpdated).bindFromRequest().fold(
        formWithErrors => Future.successful(
          // Nowhere to show a validation error so just fall back to an error page
          showErrors(formWithErrors.errors.map { e => ServiceError(e.format) })
        ),
        version =>
          appointments.deleteNote(request.appointment.id, note.id, version).successMap { _ =>
            Redirect(controllers.admin.routes.AppointmentController.view(appointmentKey))
              .flashing("success" -> Messages("flash.appointment.noteDeleted"))
          }
      )
    }
  }

  private def withAppointmentNote(id: UUID)(f: AppointmentNote => Future[Result])(implicit request: AppointmentSpecificRequest[AnyContent]): Future[Result] =
    appointments.getNotes(request.appointment.id).successFlatMap { notes =>
      notes.find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

}
