package domain

import enumeratum.{Enum, EnumEntry}
import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsPath, JsString}

import scala.collection.immutable

sealed abstract class ReasonableAdjustment(override val entryName: String, val description: String) extends EnumEntry with IdAndDescription {
  val id: String = entryName
}

object ReasonableAdjustment extends Enum[ReasonableAdjustment] {
  case object Extra25 extends ReasonableAdjustment("Extra25", "25% extra time in exams")
  case object Extra50 extends ReasonableAdjustment("Extra50", "50% extra time in exams")
  case object SmallerRoom extends ReasonableAdjustment("SmallerRoom", "Smaller room for exams")
  case object SoleRoom extends ReasonableAdjustment("SoleRoom", "Sole room for exams")
  case object ExtendedDeadlines extends ReasonableAdjustment("ExtendedDeadlines", "Extended deadlines for assignments")
  case object AlternativeAssessment extends ReasonableAdjustment("AlternativeAssessment", "Alternative mode of assessment")
  case object SeatArrangement extends ReasonableAdjustment("SeatArrangement", "Seat arrangement (door/window/front/rear of room)")
  case object SuggestionsAnxious extends ReasonableAdjustment("SuggestionsAnxious", "Suggestions for managing anxious students")
  case object RecordingDevice extends ReasonableAdjustment("RecordingDevice", "Recording device in lecture")
  case object Reader extends ReasonableAdjustment("Reader", "Reader")
  case object Scribe extends ReasonableAdjustment("Scribe", "Scribe")
  case object ExamFood extends ReasonableAdjustment("ExamFood", "Take food into exam")
  case object ExamMedication extends ReasonableAdjustment("ExamMedication", "Take medication into exam")
  case object Exam5 extends ReasonableAdjustment("Exam5", "Discounted exam rest break up to 5 mins/hr")
  case object Exam10 extends ReasonableAdjustment("Exam10", "Discounted exam rest break up to 10 mins/hr")
  case object Exam15 extends ReasonableAdjustment("Exam15", "Discounted exam rest break up to 15 mins/hr")
  case object SpecialistAccommodation extends ReasonableAdjustment("SpecialistAccommodation", "Specialist accommodation due to MH/Disability need")
  case object Other extends ReasonableAdjustment("Other", "Other")

  val values: immutable.IndexedSeq[ReasonableAdjustment] = findValues

  object Formatter extends Formatter[ReasonableAdjustment] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ReasonableAdjustment] = {
      data.get(key).map(id =>
        values.find(_.entryName == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.reasonableadjustment.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: ReasonableAdjustment): Map[String, String] = Map(
      key -> value.entryName
    )
  }

  implicit val reasonableAdjustmentFormatter: Format[ReasonableAdjustment] = Format(
    JsPath.read[String].map[ReasonableAdjustment](id => values.find(_.entryName == id).getOrElse(throw new IllegalArgumentException(s"Unknown reasonable adjustment id $id"))),
    (o: ReasonableAdjustment) => JsString(o.entryName)
  )
}
