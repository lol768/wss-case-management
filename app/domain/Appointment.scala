package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import enumeratum.{EnumEntry, PlayEnum}
import play.api.libs.json.{Json, Writes}
import uk.ac.warwick.util.termdates.AcademicYear

import scala.collection.immutable

case class Appointment(
  id: UUID,
  key: IssueKey,
  start: OffsetDateTime,
  duration: Duration,
  team: Team,
  appointmentType: AppointmentType,
  purpose: AppointmentPurpose,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason],
  outcome: Option[AppointmentOutcome],
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) {
  val end: OffsetDateTime = start.plus(duration)

  def subject(clientsOption: Option[Set[AppointmentClient]], teamMembersOption: Option[Set[AppointmentTeamMember]]): String = Seq(
    clientsOption.map(clients =>
      if (clients.size == 1) {
        s"${clients.head.client.safeFullName}, "
      } else {
        s"${clients.size} clients, "
      }
    ).getOrElse(""),
    appointmentType.description.toLowerCase(),
    " with ",
    teamMembersOption.map(_.map(_.member.safeFullName).mkString(", ")).getOrElse(team.name),
  ).mkString
}

object Appointment {
  def tupled = (apply _).tupled

  val DurationOptions: Seq[(String, Duration)] = Seq(
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
  appointmentType: AppointmentType,
  purpose: AppointmentPurpose,
)

case class AppointmentRender(
  appointment: Appointment,
  clients: Set[AppointmentClient],
  teamMembers: Set[AppointmentTeamMember],
  room: Option[Room],
  clientCases: Set[Case],
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
    "purpose" -> Json.obj(
      "id" -> o.appointment.purpose,
      "description" -> o.appointment.purpose.description,
    ),
    "state" -> o.appointment.state,
    "cancellationReason" -> o.appointment.cancellationReason,
    "outcome" -> o.appointment.outcome.map { outcome =>
      Json.obj(
        "id" -> outcome,
        "description" -> outcome.description,
      )
    },
    "cases" -> o.clientCases.map { clientCase =>
      Json.obj(
        "id" -> clientCase.id,
        "key" -> clientCase.key.string,
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
  member: Member,
  outlookId: Option[String]
)

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

sealed abstract class AppointmentState(val labelType: String, val isTerminal: Boolean) extends EnumEntry {
  // When attached to AppointmentClient, the displayable description
  val clientDescription: String = entryName
  val nonTerminal: Boolean = !isTerminal
}
object AppointmentState extends PlayEnum[AppointmentState] {
  case object Provisional extends AppointmentState("default", isTerminal = false)
  case object Accepted extends AppointmentState("info", isTerminal = false)
  case object Attended extends AppointmentState("success", isTerminal = true)
  case object Cancelled extends AppointmentState("danger", isTerminal = true) {
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

sealed abstract class AppointmentOutcome(val description: String) extends EnumEntry
object AppointmentOutcome extends PlayEnum[AppointmentOutcome] {
  case object BlueSky extends AppointmentOutcome("Advised to contact BlueSky")
  case object Careers extends AppointmentOutcome("Advised to contact Careers")
  case object Chaplaincy extends AppointmentOutcome("Advised to contact Chaplaincy")
  case object CRASAC extends AppointmentOutcome("Advised to contact CRASAC")
  case object IAPT extends AppointmentOutcome("Advised to contact IAPT")
  case object Relate extends AppointmentOutcome("Advised to contact Relate")
  case object PersonalTutor extends AppointmentOutcome("Advised to contact Personal Tutor/Senior Tutor")
  case object BACP extends AppointmentOutcome("Advised to contact BACP (private)")
  case object UKCP extends AppointmentOutcome("Advised to contact UKCP / BPS")
  case object EatingDisorderClinic extends AppointmentOutcome("Advised to contact Eating Disorder Clinic")
  case object UniversityDean extends AppointmentOutcome("Advised to contact University Dean of Students")
  case object GP extends AppointmentOutcome("Advised to contact GP/Statutory services")
  case object SocialServices extends AppointmentOutcome("Advised to contact Social Services")
  case object StudentFunding extends AppointmentOutcome("Advised to contact Student Funding team")
  case object StudentUnion extends AppointmentOutcome("Advised to contact Student Union Advice Centre")
  case object Funding extends AppointmentOutcome("Advised to contact funding body")
  case object SupportWorker extends AppointmentOutcome("Agreed provision of support worker - bank 1 or 2")
  case object StudySkills extends AppointmentOutcome("Agreed provision of 1-1 study skills")
  case object AcademicMentoring extends AppointmentOutcome("Agreed provision of 1-1 academic mentoring")
  case object MentalHealth extends AppointmentOutcome("Agreed provision of 1-1 mental health mentoring")
  case object Additional extends AppointmentOutcome("Additional appointment/session booked")
  case object Further extends AppointmentOutcome("Further investigation")
  case object Disciplinary extends AppointmentOutcome("Disciplinary")
  case object Fitness extends AppointmentOutcome("Fitness to attend")
  case object NoFurtherAction extends AppointmentOutcome("No further action required")
  case object Closed extends AppointmentOutcome("Case closed")
  case object Other extends AppointmentOutcome("Other")

  override def values: immutable.IndexedSeq[AppointmentOutcome] = findValues.sortBy(_.description)
}