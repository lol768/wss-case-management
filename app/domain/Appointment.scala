package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.dao.CaseDao.Case
import enumeratum.{EnumEntry, PlayEnum}
import helpers.JavaTime
import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import warwick.sso.{UniversityID, User, Usercode}

import scala.collection.immutable
import scala.util.Try

case class Appointment(
  id: UUID,
  key: IssueKey,
  start: OffsetDateTime,
  duration: Duration,
  location: Option[Location],
  team: Team,
  teamMember: Usercode,
  appointmentType: AppointmentType,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason],
  created: OffsetDateTime,
  lastUpdated: OffsetDateTime,
) {
  val end: OffsetDateTime = start.plus(duration)

  def subject(
    userLookupOption: Option[Map[Usercode, User]],
    clientLookupOption: Option[Set[Either[UniversityID, SitsProfile]]]
  ): String = "%s with %s%s%s".format(
    appointmentType.description,
    userLookupOption.map(userLookup =>
      "%s".format(userLookup.get(teamMember).flatMap(_.name.full).getOrElse(teamMember.string))
    ).getOrElse(""),
    if (userLookupOption.nonEmpty && clientLookupOption.nonEmpty) " and " else "",
    clientLookupOption.map(clientLookup =>
      if (clientLookup.size == 1) {
        clientLookup.head.fold(
          universityID => universityID.string,
          sitsProfile => sitsProfile.fullName
        )
      } else {
        s"${clientLookup.size} clients"
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
  location: Option[Location],
  teamMember: Usercode,
  appointmentType: AppointmentType
)

case class AppointmentRender(
  appointment: Appointment,
  clients: Set[AppointmentClient],
  clientCases: Set[Case],
  notes: Seq[AppointmentNote]
)

case class AppointmentClient(
  universityID: UniversityID,
  state: AppointmentState,
  cancellationReason: Option[AppointmentCancellationReason]
)

sealed abstract class Location {
  def name: String
  override def toString: String = name
}

object Location {
  def toFormattedString(l: Location): String = l match {
    case NamedLocation(name) => name
    case MapLocation(name, locationId, syllabusPlusName) => s"$name|$locationId${syllabusPlusName.map(n => s"|$n").getOrElse("")}"
  }

  def fromFormattedString(s: String): Location =
    s.split("\\|", 3) match {
      case Array(name, locationId, syllabusPlusName) => MapLocation(name, locationId, Some(syllabusPlusName))
      case Array(name, locationId) => MapLocation(name, locationId)
      case Array(name) => NamedLocation(name)
    }

  object Formatter extends Formatter[Location] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Location] = {
      data.get(key).map(formatted =>
        Try(fromFormattedString(formatted)).toOption.map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.location.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: Location): Map[String, String] = Map(
      key -> toFormattedString(value)
    )
  }

  val formField: Mapping[Location] = Forms.of(Formatter)
}

case class NamedLocation(name: String) extends Location
case class MapLocation(name: String, locationId: String, syllabusPlusName: Option[String] = None) extends Location

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
  teamMember: Usercode,
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