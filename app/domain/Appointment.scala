package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.dao.CaseDao.Case
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.libs.json.{Json, Writes}
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.sso.Usercode

import scala.collection.immutable

case class Appointment(
  id: UUID,
  key: IssueKey,
  start: OffsetDateTime,
  duration: Duration,
  team: Team,
  appointmentType: AppointmentType,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason],
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) {
  val end: OffsetDateTime = start.plus(duration)

  def subject(clientsOption: Option[Set[AppointmentClient]], teamMembersOption: Option[Set[AppointmentTeamMember]]): String = Seq(
    appointmentType.description,
    " with ",
    teamMembersOption.map(_.map(_.member.safeFullName).mkString(", ")).getOrElse(team.name),
    if (clientsOption.nonEmpty) " and " else "",
    clientsOption.map(clients =>
      if (clients.size == 1) {
        clients.head.client.safeFullName
      } else {
        s"${clients.size} clients"
      }
    ).getOrElse("")
  ).mkString
}

object Appointment {
  def tupled = (apply _).tupled

  val DurationOptions = Seq(
    ("10 minutes", Duration.ofMinutes(10)),
    ("15 minutes", Duration.ofMinutes(15)),
    ("20 minutes", Duration.ofMinutes(20)),
    ("30 minutes", Duration.ofMinutes(30)),
    ("45 minutes", Duration.ofMinutes(45)),
    ("1 hour", Duration.ofHours(1)),
    ("1 hour 30 minutes", Duration.ofMinutes(90)),
    ("2 hours", Duration.ofHours(2))
  )
}

/**
  * Just enough information to save or update an appointment
  */
case class AppointmentSave(
  start: OffsetDateTime,
  duration: Duration,
  roomID: Option[UUID],
  appointmentType: AppointmentType
)

case class AppointmentRender(
  appointment: Appointment,
  clients: Set[AppointmentClient],
  teamMembers: Set[AppointmentTeamMember],
  room: Option[Room],
  clientCases: Set[Case],
  notes: Seq[AppointmentNote]
)

object AppointmentRender {
  def writer: Writes[AppointmentRender] = (o: AppointmentRender) => Json.obj(
    "id" -> o.appointment.id,
    "key" -> o.appointment.key.string,
    "subject" -> o.appointment.subject(Some(o.clients), Some(o.teamMembers)),
    "start" -> o.appointment.start,
    "weekNumber" -> AcademicYear.forDate(o.appointment.start).getAcademicWeek(o.appointment.start).getWeekNumber,
    "end" -> o.appointment.end,
    "url" -> controllers.admin.routes.AppointmentController.view(o.appointment.key).url,
    "duration" -> o.appointment.duration,
    "location" -> Json.toJsFieldJsValueWrapper(o.room)(Writes.optionWithNull(Room.writer)),
    "team" -> Json.toJsFieldJsValueWrapper(o.appointment.team)(Teams.writer),
    "teamMembers" -> o.teamMembers.map { teamMember =>
      Json.obj(
        "usercode" -> teamMember.member.usercode.string,
        "fullName" -> teamMember.member.safeFullName,
      )
    },
    "appointmentType" -> Json.obj(
      "id" -> o.appointment.appointmentType,
      "description" -> o.appointment.appointmentType.description,
    ),
    "state" -> o.appointment.state,
    "cancellationReason" -> o.appointment.cancellationReason,
    "cases" -> o.clientCases.map { clientCase =>
      Json.obj(
        "id" -> clientCase.id,
        "key" -> clientCase.key.map(_.string),
        "subject" -> clientCase.subject,
      )
    },
    "clients" -> o.clients.map { client =>
      Json.obj(
        "client" -> Json.obj(
          "universityID" -> client.client.universityID.string,
          "fullName" -> client.client.safeFullName,
        ),
        "state" -> client.state,
        "cancellationReason" -> client.cancellationReason,
      )
    },
    "created" -> o.appointment.created,
    "lastUpdated" -> o.appointment.lastUpdated,
  )
}

case class AppointmentClient(
  client: Client,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason]
)

case class AppointmentTeamMember(
  member: Member
)

sealed abstract class AppointmentTypeCategory(val description: String) extends EnumEntry
object AppointmentTypeCategory extends PlayEnum[AppointmentTypeCategory] {
  case object FaceToFace extends AppointmentTypeCategory("Face to face")
  case object Online extends AppointmentTypeCategory("Telephone / Skype")
  case object Email extends AppointmentTypeCategory("Email")

  override def values: immutable.IndexedSeq[AppointmentTypeCategory] = findValues
}

sealed abstract class AppointmentType(val category: AppointmentTypeCategory, val description: String) extends EnumEntry
object AppointmentType extends PlayEnum[AppointmentType] {
  case object FaceToFace extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Face to face")
  case object Workshop extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Workshop")
  case object Consultation extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Consultation session")
  case object DropIn extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Drop in")
  case object Duty extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Duty")
  case object InitialAssessment extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Initial assessment")
  case object FollowUp extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Follow up")
  case object Mentoring extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Mentoring")
  case object GroupTherapy extends AppointmentType(AppointmentTypeCategory.FaceToFace, "Group therapy")
  case object Telephone extends AppointmentType(AppointmentTypeCategory.Online, "Telephone")
  case object Skype extends AppointmentType(AppointmentTypeCategory.Online, "Skype")
  case object Email extends AppointmentType(AppointmentTypeCategory.Email, "Email")

  override def values: immutable.IndexedSeq[AppointmentType] = findValues
}

sealed abstract class AppointmentState(val labelType: String) extends EnumEntry {
  // When attached to AppointmentClient, the displayable description
  val clientDescription: String = entryName
}
object AppointmentState extends PlayEnum[AppointmentState] {
  case object Provisional extends AppointmentState("default")
  case object Accepted extends AppointmentState("info")
  case object Attended extends AppointmentState("success")
  case object Cancelled extends AppointmentState("danger") {
    override val clientDescription = "Declined"
  }

  override def values: immutable.IndexedSeq[AppointmentState] = findValues
}

sealed abstract class AppointmentCancellationReason(val description: String) extends EnumEntry
object AppointmentCancellationReason extends PlayEnum[AppointmentCancellationReason] {
  case object UnableToAttend extends AppointmentCancellationReason("Unable to attend")
  case object NoLongerNeeded extends AppointmentCancellationReason("No longer needed")
  case object Clash extends AppointmentCancellationReason("Appointment clash")
  case object Other extends AppointmentCancellationReason("Other")

  override def values: immutable.IndexedSeq[AppointmentCancellationReason] = findValues
}

case class AppointmentNote(
  id: UUID,
  text: String,
  teamMember: Member,
  created: OffsetDateTime = OffsetDateTime.now(),
  lastUpdated: OffsetDateTime = OffsetDateTime.now()
)

object AppointmentNote {
  // oldest first
  val dateOrdering: Ordering[AppointmentNote] = Ordering.by[AppointmentNote, OffsetDateTime](_.created)(JavaTime.dateTimeOrdering)
}

/**
  * Just the data of a note required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class AppointmentNoteSave(
  text: String,
  teamMember: Usercode
)