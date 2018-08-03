package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsPath, JsString}

object Medication {
  implicit val medicationFormatter: Format[Medication] = Format(
    JsPath.read[String].map[Medication](id => Medications.all.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown medication id $id"))),
    (o: Medication) => JsString(o.id)
  )
}

sealed abstract class Medication(val id: String, val description: String)

object Medications {
  case object Antidepressant extends Medication("Antidepressant", "Antidepressant")
  case object Antipsychotic extends Medication("Antipsychotic", "Antipsychotic")
  case object Anxiolytic extends Medication("Anxiolytic", "Anxiolytic")
  case object Benzodiazepine extends Medication("Benzodiazepine", "Benzodiazepine")
  case object BetaBlocker extends Medication("Beta blocker", "Beta blocker")
  case object Hypnotic extends Medication("Hypnotic", "Hypnotic")
  case object MoodStabiliser extends Medication("Mood stabiliser", "Mood stabiliser")
  case object Stimulant extends Medication("Stimulant", "Stimulant")
  case object Other extends Medication("Other", "Other")

  def all = Seq(Antidepressant, Antipsychotic, Anxiolytic, Benzodiazepine, BetaBlocker, Hypnotic, MoodStabiliser, Stimulant, Other)

  object Formatter extends Formatter[Medication] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Medication] = {
      data.get(key).map(id =>
        all.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.medication.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: Medication): Map[String, String] = Map(
      key -> value.id
    )
  }
}
