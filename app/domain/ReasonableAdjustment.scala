package domain

import enumeratum.{Enum, EnumEntry}
import play.api.data.{FormError, Forms, Mapping}
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsPath, JsString}

import scala.collection.immutable

sealed abstract class ReasonableAdjustment(val description: String) extends EnumEntry with IdAndDescription {
  val id: String = entryName
}

object ReasonableAdjustment extends Enum[ReasonableAdjustment] {
  case object Extra25 extends ReasonableAdjustment("25% extra time in exams")
  case object Extra50 extends ReasonableAdjustment("50% extra time in exams")
  case object SmallerRoom extends ReasonableAdjustment("Smaller room for exams")
  case object SoleRoom extends ReasonableAdjustment("Sole room for exams")
  case object ExtendedDeadlines extends ReasonableAdjustment("Extended deadlines for assignments")
  case object AlternativeAssessment extends ReasonableAdjustment("Alternative mode of assessment")
  case object SeatArrangement extends ReasonableAdjustment("Seat arrangement (door/window/front/rear of room)")
  case object SuggestionsAnxious extends ReasonableAdjustment("Suggestions for managing anxious students")
  case object RecordingDevice extends ReasonableAdjustment("Recording device in lecture")
  case object Reader extends ReasonableAdjustment("Reader")
  case object Scribe extends ReasonableAdjustment("Scribe")
  case object ExamFood extends ReasonableAdjustment("Take food into exam")
  case object ExamMedication extends ReasonableAdjustment("Take medication into exam")
  case object Exam5 extends ReasonableAdjustment("Discounted exam rest break up to 5 mins/hr")
  case object Exam10 extends ReasonableAdjustment("Discounted exam rest break up to 10 mins/hr")
  case object Exam15 extends ReasonableAdjustment("Discounted exam rest break up to 15 mins/hr")
  case object SpecialistAccommodation extends ReasonableAdjustment("Specialist accommodation due to MH/Disability need")
  case object Other extends ReasonableAdjustment("Other")

  val values: immutable.IndexedSeq[ReasonableAdjustment] = findValues

  // Used to display in two columns in a view
  val partioned: Seq[(Option[ReasonableAdjustment], Option[ReasonableAdjustment])] = {
    val leftQuantity = Math.ceil(values.size.toDouble / 2).toInt
    val rightQuantity = values.size - leftQuantity
    values.take(leftQuantity).map(Option.apply).zipAll(
      values.takeRight(rightQuantity).map(Option.apply),
      None,
      None
    )
  }

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

  val formField: Mapping[ReasonableAdjustment] = Forms.of(Formatter)

  implicit val reasonableAdjustmentFormatter: Format[ReasonableAdjustment] = Format(
    JsPath.read[String].map[ReasonableAdjustment](id => values.find(_.entryName == id).getOrElse(throw new IllegalArgumentException(s"Unknown reasonable adjustment id $id"))),
    (o: ReasonableAdjustment) => JsString(o.entryName)
  )
}
