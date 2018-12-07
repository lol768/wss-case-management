package controllers.admin

import java.time.format.DateTimeFormatter
import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import controllers.admin.AppointmentController._
import controllers.refiners._
import controllers.{API, BaseController}
import domain._
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import helpers.{FormHelpers, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, Result}
import services.FreeBusyService.FreeBusyPeriod
import services._
import services.tabula.ProfileService
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object AppointmentController {
  sealed trait Mode extends EnumEntry
  object Mode extends PlayEnum[Mode] {
    case object Create extends Mode
    case object Edit extends Mode
    case object Reschedule extends Mode

    override def values: immutable.IndexedSeq[Mode] = findValues
  }

  case class AppointmentFormData(
    clients: Set[UniversityID],
    teamMembers: Set[Usercode],
    cases: Set[UUID],
    appointment: AppointmentSave,
    version: Option[OffsetDateTime]
  )

  def form(
    team: Team,
    profileService: ProfileService,
    caseService: CaseService,
    permissionService: PermissionService,
    locationService: LocationService,
    existingClients: Set[Client],
    isRescheduling: Boolean,
    existingVersion: Option[OffsetDateTime] = None
  )(implicit t: TimingContext, executionContext: ExecutionContext): Form[AppointmentFormData] = {
    def isValid(u: UniversityID, existing: Set[Client]): Boolean =
      existing.exists(_.universityID == u) ||
        Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
          .toOption.exists(_.exists(_.nonEmpty))

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
        "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u, existingClients))).verifying("error.required", _.exists(_.string.nonEmpty)),
        "teamMembers" -> set(
          optional(text).transform[Option[Usercode]](_.map(Usercode.apply), _.map(_.string))
            .verifying("error.appointment.teamMember.invalid", u => u.isEmpty || u.exists { usercode => isValidTeamMember(usercode) })
        ).transform[Set[Usercode]](_.flatten, _.map(Some.apply)).verifying("error.required", _.nonEmpty),
        "cases" -> set(
          optional(uuid.verifying("error.appointment.cases.invalid", id => isValidCase(id)))
        ).transform[Set[UUID]](_.flatten, _.map(Some.apply)).verifying("error.appointment.cases.nonEmpty", _.nonEmpty),
        "appointment" -> mapping(
          "start" -> FormHelpers.offsetDateTime.verifying("error.appointment.start.inPast", dt => (existingVersion.nonEmpty && !isRescheduling) || dt.isAfter(JavaTime.offsetDateTime)),
          "duration" -> number(min = 60, max = 120 * 60).transform[Duration](n => Duration.ofSeconds(n.toLong), _.getSeconds.toInt),
          "roomID" -> optional(uuid.verifying("error.required", id => isValidRoom(id))),
          "appointmentType" -> AppointmentType.formField,
          "purpose" -> AppointmentPurpose.formField,
        )(AppointmentSave.apply)(AppointmentSave.unapply),
        "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
      )(AppointmentFormData.apply)(AppointmentFormData.unapply)
    )
  }

  case class CancelAppointmentData(
    cancellationReason: AppointmentCancellationReason,
    message: Option[String],
    version: OffsetDateTime
  )

  def cancelForm(existingVersion: OffsetDateTime): Form[CancelAppointmentData] =
    Form(
      mapping(
        "cancellationReason" -> AppointmentCancellationReason.formField,
        "message" -> optional(text),
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

  case class FreeBusyForm(
    clients: Set[UniversityID],
    teamMembers: Set[Usercode],
    roomIDs: Set[UUID]
  )

  def freeBusyForm(profileService: ProfileService, permissionService: PermissionService, locationService: LocationService)(implicit t: TimingContext, executionContext: ExecutionContext): Form[FreeBusyForm] = {
    def isValid(u: UniversityID): Boolean =
      Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
        .toOption.exists(_.isRight)

    def isValidTeamMember(usercode: Usercode): Boolean =
      Try(Await.result(permissionService.inAnyTeam(usercode), 5.seconds))
        .toOption.exists {
        case Right(true) => true
        case _ => false
      }

    def isValidRoom(id: UUID): Boolean =
      Try(Await.result(locationService.findRoom(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(
      mapping(
        "clients" -> set(nonEmptyText.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u))),
        "teamMembers" -> set(nonEmptyText.transform[Usercode](Usercode.apply, _.string).verifying("error.appointment.teamMember.invalid", u => isValidTeamMember(u))),
        "roomIDs" -> set(uuid.verifying("error.required", id => isValidRoom(id)))
      )(FreeBusyForm.apply)(FreeBusyForm.unapply)
    )
  }

  case class FreeBusyResource(id: String, title: String, `type`: String)
}

@Singleton
class AppointmentController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  appointments: AppointmentService,
  userLookupService: UserLookupService,
  permissions: PermissionService,
  locations: LocationService,
  freeBusyService: FreeBusyService,
  clients: ClientService,
  members: MemberService,
  permissionService: PermissionService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  appointmentActionFilters: AppointmentActionFilters,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import appointmentActionFilters._
  import canViewTeamActionRefiner._

  private def renderAppointment(appointmentKey: IssueKey, cancelForm: Form[CancelAppointmentData])(implicit request: AppointmentSpecificRequest[AnyContent]): Future[Result] =
    ServiceResults.zip(
      appointments.findForRender(appointmentKey),
      permissionService.canEditAppointment(currentUser().usercode, request.appointment.id)
    )
   .successFlatMap { case (render, canEdit) =>
      profiles.getProfiles(render.clients.map(_.client.universityID)).successMap { clientProfiles =>
        val clients = render.clients.map(c => c -> clientProfiles.get(c.client.universityID)).toMap

        Ok(views.html.admin.appointments.view(
          render,
          clients,
          cancelForm,
          canEdit
        ))
      }
    }

  def view(appointmentKey: IssueKey): Action[AnyContent] = CanViewAppointmentAction(appointmentKey).async { implicit request =>
    renderAppointment(
      appointmentKey,
      cancelForm(request.appointment.lastUpdated)
        .bind(JavaTime.OffsetDateTimeFormatter.unbind("version", request.appointment.lastUpdated))
        .discardingErrors
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
    val baseForm = form(teamRequest.team, profiles, cases, permissions, locations, Set(), isRescheduling = false)
    val baseBind = Map("teamMembers[0]" -> teamRequest.context.user.get.usercode.string)

    if (forCase.nonEmpty && client.nonEmpty) {
      Future.successful(
        BadRequest("Can't specify both forCase and client")
      )
    } else {
      val binds: Seq[Future[Map[String, String]]] = Seq(
        Some(Future.successful(baseBind)),
        forCase.map { caseKey =>
          cases.find(caseKey).flatMap(_.fold(
            _ => Future.successful(Map.empty[String, String]),
            clientCase => cases.getClients(clientCase.id).map(_.fold(
              _ => Map("cases[0]" -> clientCase.id.toString),
              clients => Map(
                "cases[0]" -> clientCase.id.toString
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
        locations.availableRooms.successMap { availableRooms =>
          Ok(views.html.admin.appointments.create(
            teamRequest.team,
            baseForm.bind(bind).discardingErrors,
            availableRooms
          ))
        }
      }
    }
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val boundForm = form(teamRequest.team, profiles, cases, permissions, locations, Set(), isRescheduling = false).bindFromRequest
    boundForm.fold(
      formWithErrors => {
        locations.availableRooms.successMap { availableRooms =>
          Ok(views.html.admin.appointments.create(teamRequest.team, formWithErrors, availableRooms))
        }
      },
      data => {
        val clients = data.clients.filter(_.string.nonEmpty)
        val linkedCases = data.cases.filter(_.toString.nonEmpty)
        cases.getClients(linkedCases).successFlatMap { clientMap =>
          val caseClients = clientMap.values.flatten.toSet
          val nonCaseClients = clients.filterNot(c => caseClients.exists(_.universityID == c))
          if (nonCaseClients.nonEmpty) {
            val formWithErrors = boundForm.withError(FormError("clients", "error.appointment.clients.invalid", Seq(nonCaseClients.map(_.string).mkString(", "))))
            locations.availableRooms.successMap { availableRooms =>
              Ok(views.html.admin.appointments.create(teamRequest.team, formWithErrors, availableRooms))
            }
          } else {
            appointments.create(data.appointment, clients, data.teamMembers, teamRequest.team, linkedCases, currentUser().usercode).successMap { appointment =>
              Redirect(controllers.admin.routes.AppointmentController.view(appointment.key))
                .flashing("success" -> Messages("flash.appointment.created", appointment.key.string))
            }
          }
        }
      }
    )
  }

  private def appointmentSave(a: AppointmentRender): AppointmentSave =
    AppointmentSave(
      a.appointment.start,
      a.appointment.duration,
      a.room.map(_.id),
      a.appointment.appointmentType,
      a.appointment.purpose,
    )

  def editForm(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successMap { case (a, availableRooms) =>
      Ok(
        views.html.admin.appointments.edit(
          a,
          form(a.appointment.team, profiles, cases, permissions, locations, a.clients.map(_.client), isRescheduling = false, Some(a.appointment.lastUpdated))
            .fill(AppointmentFormData(
              a.clients.map(_.client.universityID),
              a.teamMembers.map(_.member.usercode),
              a.clientCases.map(_.id),
              appointmentSave(a),
              Some(a.appointment.lastUpdated)
            )),
          availableRooms
        )
      )
    }
  }

  def edit(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successFlatMap { case (a, availableRooms) =>
      val boundForm = form(a.appointment.team, profiles, cases, permissions, locations, a.clients.map(_.client), isRescheduling = false, Some(a.appointment.lastUpdated)).bindFromRequest
      boundForm.fold(
        formWithErrors => Future.successful(Ok(views.html.admin.appointments.edit(a, formWithErrors, availableRooms))),
        data => {
          val clients = data.clients.filter(_.string.nonEmpty)
          val linkedCases = data.cases.filter(_.toString.nonEmpty)
          cases.getClients(linkedCases).successFlatMap { clientMap =>
            val caseClients = clientMap.values.flatten.toSet
            val nonCaseClients = clients.filterNot(c => caseClients.exists(_.universityID == c))
            if (nonCaseClients.nonEmpty) {
              val formWithErrors = boundForm.withError(FormError("clients", "error.appointment.clients.invalid", Seq(nonCaseClients.map(_.string).mkString(", "))))
              locations.availableRooms.successMap { availableRooms =>
                Ok(views.html.admin.appointments.edit(a, formWithErrors, availableRooms))
              }
            } else {
              // Don't allow re-scheduling here
              val updates = AppointmentSave(
                start = a.appointment.start,
                duration = a.appointment.duration,
                roomID = a.room.map(_.id),
                appointmentType = data.appointment.appointmentType,
                purpose = data.appointment.purpose,
              )

              appointments.update(a.appointment.id, updates, linkedCases, clients, data.teamMembers, currentUser().usercode, data.version.get).successMap { updated =>
                Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
                  .flashing("success" -> Messages("flash.appointment.updated"))
              }
            }
          }
        }
      )
    }
  }

  def freeBusy(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    // These are only options to avoid us having to specify them when referencing the route from a view
    if (start.isEmpty || end.isEmpty) {
      Future.successful(BadRequest(Json.toJson(API.Failure[JsValue](
        status = "bad-request",
        errors = Seq(API.Error("error.required", "start and end parameters must be supplied"))
      ))))
    } else {
      freeBusyForm(profiles, permissions, locations).bindFromRequest().fold(
        formWithErrors => Future.successful(
          BadRequest(Json.toJson(API.Failure[JsValue](
            status = "bad-request",
            errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
          )))
        ),
        form => {
          val futures: Seq[Future[ServiceResult[Seq[(String, FreeBusyPeriod)]]]] =
            form.clients.toSeq.map { client =>
              freeBusyService.findFreeBusyPeriods(client, start.get.toLocalDate, end.get.toLocalDate)
                .map(_.value.right.map(_.map(client.string -> _)))
            } ++
            form.teamMembers.toSeq.map { teamMember =>
              freeBusyService.findFreeBusyPeriods(teamMember, start.get.toLocalDate, end.get.toLocalDate)
                .map(_.value.right.map(_.map(teamMember.string -> _)))
            } ++
            form.roomIDs.toSeq.map { roomID =>
              freeBusyService.findFreeBusyPeriods(roomID, start.get.toLocalDate, end.get.toLocalDate)
                .map(_.value.right.map(_.map(roomID.toString -> _)))
            }

          ServiceResults.futureSequence(futures).successMap { periods =>
            Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(periods.flatten)(Writes.seq { case (resourceId: String, period: FreeBusyPeriod) =>
              Json.obj(
                "id" -> s"$resourceId-${period.status}-${period.start}",
                "resourceId" -> resourceId,
                "title" -> period.status.description,
                "allDay" -> false,
                "start" -> period.start,
                "end" -> period.end,
                "className" -> period.status.entryName,
              )
            }))))
          }
        }
      )
    }
  }

  def freeBusyResources(): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    freeBusyForm(profiles, permissions, locations).bindFromRequest().fold(
      formWithErrors => Future.successful(
        BadRequest(Json.toJson(API.Failure[JsValue](
          status = "bad-request",
          errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
        )))
      ),
      form => {
        val futures: Seq[Future[ServiceResult[Seq[FreeBusyResource]]]] =
          clients.getOrAddClients(form.clients)
            .map(_.right.map(_.map { c => FreeBusyResource(c.universityID.string, c.fullName.getOrElse(c.universityID.string), "client") })) +:
          members.getOrAddMembers(form.teamMembers)
            .map(_.right.map(_.map { m => FreeBusyResource(m.usercode.string, m.fullName.getOrElse(m.usercode.string), "team")})) +:
          form.roomIDs.toSeq.map { roomID =>
            locations.findRoom(roomID).map(_.right.map { r => Seq(FreeBusyResource(r.id.toString, r.name, "room")) })
          }

        ServiceResults.futureSequence(futures).successMap { resources =>
          Ok(Json.toJson(API.Success[JsValue](data = Json.toJson(resources.flatten)(Writes.seq(Json.writes[FreeBusyResource])))))
        }
      }
    )
  }

  def rescheduleForm(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successMap { case (a, availableRooms) =>
      // Don't allow rescheduling appointments that are in a terminal state
      if (a.appointment.state.isTerminal)
        Redirect(controllers.admin.routes.AppointmentController.view(a.appointment.key))
      else
        Ok(
          views.html.admin.appointments.reschedule(
            a,
            form(a.appointment.team, profiles, cases, permissions, locations, a.clients.map(_.client), isRescheduling = true, Some(a.appointment.lastUpdated))
              .fill(AppointmentFormData(
                a.clients.map(_.client.universityID),
                a.teamMembers.map(_.member.usercode),
                a.clientCases.map(_.id),
                AppointmentSave(
                  a.appointment.start,
                  a.appointment.duration,
                  a.room.map(_.id),
                  a.appointment.appointmentType,
                  a.appointment.purpose,
                ),
                Some(a.appointment.lastUpdated)
              )),
            availableRooms
          )
        )
    }
  }

  def reschedule(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    ServiceResults.zip(appointments.findForRender(appointmentKey), locations.availableRooms).successFlatMap { case (a, availableRooms) =>
      // Don't allow rescheduling appointments that are in a terminal state
      if (a.appointment.state.isTerminal) {
        Future.successful(Redirect(controllers.admin.routes.AppointmentController.view(a.appointment.key)))
      } else {
        val boundForm = form(a.appointment.team, profiles, cases, permissions, locations, a.clients.map(_.client), isRescheduling = true, Some(a.appointment.lastUpdated)).bindFromRequest
        boundForm.fold(
          formWithErrors => Future.successful(Ok(views.html.admin.appointments.reschedule(a, formWithErrors, availableRooms))),
          data => {
            val updates = AppointmentSave(
              start = data.appointment.start,
              duration = data.appointment.duration,
              roomID = data.appointment.roomID,
              appointmentType = a.appointment.appointmentType,
              purpose = a.appointment.purpose,
            )

            if (updates == appointmentSave(a)) {
              // no-op, don't send notifications unnecessarily
              Future.successful(Redirect(controllers.admin.routes.AppointmentController.view(a.appointment.key)))
            } else {
              appointments.reschedule(a.appointment.id, updates, currentUser().usercode, data.version.get).successMap { updated =>
                Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
                  .flashing("success" -> Messages("flash.appointment.updated"))
              }
            }
          }
        )
      }
    }
  }

  def cancel(appointmentKey: IssueKey): Action[AnyContent] = CanEditAppointmentAction(appointmentKey).async { implicit request =>
    val appointment = request.appointment

    cancelForm(appointment.lastUpdated).bindFromRequest().fold(
      formWithErrors => renderAppointment(
        appointmentKey,
        formWithErrors
      ),
      data => {
        val note = data.message.map(CaseNoteSave(_, currentUser().usercode, Some(appointment.id)))
        appointments.cancel(appointment.id, data.cancellationReason, note, currentUser().usercode, data.version).successMap { updated =>
          Redirect(controllers.admin.routes.AppointmentController.view(updated.key))
            .flashing("success" -> Messages("flash.appointment.cancelled"))
        }
      }
    )
  }

}
