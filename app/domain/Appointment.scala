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
  teamMember: Member,
  appointmentType: AppointmentType,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason],
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) {
  val end: OffsetDateTime = start.plus(duration)

  def subject(clientsOption: Option[Set[AppointmentClient]]): String = "%s with %s%s%s".format(
    appointmentType.description,
    teamMember.safeFullName,
    if (clientsOption.nonEmpty) " and " else "",
    clientsOption.map(clients =>
      if (clients.size == 1) {
        clients.head.client.safeFullName
      } else {
        s"${clients.size} clients"
      }
    ).getOrElse("")
  )
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
  teamMember: Usercode,
  appointmentType: AppointmentType
)

case class AppointmentRender(
  appointment: Appointment,
  clients: Set[AppointmentClient],
  room: Option[Room],
  clientCases: Set[Case],
  notes: Seq[AppointmentNote]
)

object AppointmentRender {
  def writer: Writes[AppointmentRender] = (o: AppointmentRender) => Json.obj(
    "id" -> o.appointment.id,
    "key" -> o.appointment.key.string,
    "subject" -> o.appointment.subject(Some(o.clients)),
    "start" -> o.appointment.start,
    "weekNumber" -> AcademicYear.forDate(o.appointment.start).getAcademicWeek(o.appointment.start).getWeekNumber,
    "end" -> o.appointment.end,
    "url" -> controllers.admin.routes.AppointmentController.view(o.appointment.key).url,
    "duration" -> o.appointment.duration,
    "location" -> Json.toJsFieldJsValueWrapper(o.room)(Writes.optionWithNull(Room.writer)),
    "team" -> Json.toJsFieldJsValueWrapper(o.appointment.team)(Teams.writer),
    "teamMember" -> Json.obj(
      "usercode" -> o.appointment.teamMember.usercode.string,
      "fullName" -> o.appointment.teamMember.fullName,
    ),
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
          "fullName" -> client.client.fullName,
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

sealed abstract class AppointmentTypeCategory(val description: String) extends EnumEntry
object AppointmentTypeCategory extends PlayEnum[AppointmentTypeCategory] {
  case object FaceToFace extends AppointmentTypeCategory("Face to face")
  case object Online extends AppointmentTypeCategory("Telephone / Skype")
  case object Email extends AppointmentTypeCategory("Email")

  override def values: immutable.IndexedSeq[AppointmentTypeCategory] = findValues
}

sealed abstract class AppointmentType(val description: String) extends EnumEntry
object AppointmentType extends PlayEnum[AppointmentType] {
  case object FaceToFace extends AppointmentType("Face to face")
  case object Skype extends AppointmentType("Skype")
  case object Telephone extends AppointmentType("Telephone")
  case object Online extends AppointmentType("Online")

  override def values: immutable.IndexedSeq[AppointmentType] = findValues
}

sealed abstract class AppointmentPurpose(val description: String) extends EnumEntry
object AppointmentPurpose extends PlayEnum[AppointmentPurpose] {
  case object Workshop extends AppointmentPurpose("Workshop")
  case object Consultation extends AppointmentPurpose("Consultation session")
  case object DropIn extends AppointmentPurpose("Drop in")
  case object Duty extends AppointmentPurpose("Duty")
  case object InitialAssessment extends AppointmentPurpose("Initial assessment")
  case object FollowUp extends AppointmentPurpose("Follow up")
  case object Mentoring extends AppointmentPurpose("Mentoring")
  case object GroupTherapy extends AppointmentPurpose("Group therapy")

  override def values: immutable.IndexedSeq[AppointmentPurpose] = findValues
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