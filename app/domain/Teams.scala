package domain

import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import play.api.libs.json.{Format, Json, Writes, _}

sealed abstract class Team(val id: String, val name: String)

trait Teamable {
  def team: Team
}

object Teams {
  case object Counselling extends Team("counselling", "Counselling Service")
  case object Disability extends Team("disability", "Disability Services")
  case object MentalHealth extends Team("mentalhealth", "Mental Health Team")
  case object WellbeingSupport extends Team("wellbeing", "Wellbeing Support")
  case object Consultation extends Team("consultation", "Consultation Team")

  val all: Seq[Team] = Seq(Counselling, Disability, MentalHealth, WellbeingSupport, Consultation)
  val none: Seq[Team] = Seq.empty

  def fromId(id: String): Team =
    all.find(_.id == id).getOrElse {
      throw new IllegalArgumentException(s"Could not find team with id $id")
    }

  object Formatter extends Formatter[Team] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Team] = {
      data.get(key).map(id =>
        all.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.team.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: Team): Map[String, String] = Map(
      key -> value.id
    )
  }

  val formField: Mapping[Team] = Forms.of(Formatter)

  val writer: Writes[Team] = (o: Team) => Json.obj(
    "id" -> o.id,
    "name" -> o.name
  )
  val format: Format[Team] = Format(
    (__ \ "id").read[String].map(fromId),
    writer
  )
}
