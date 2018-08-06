package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json._
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

object AppointmentAvailability {
  implicit val appointmentAvailabilityFormatter: Format[AppointmentAvailability] = Format(
    JsPath.read[String].map[AppointmentAvailability](id => AppointmentAvailabilities.values.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown appointment availability id $id"))),
    (o: AppointmentAvailability) => JsString(o.id)
  )
}

sealed abstract class AppointmentAvailability(
  val id: String,
  val description: String
) extends IdAndDescription with EnumEntry

object AppointmentAvailabilities extends Enum[AppointmentAvailability] {
  case object Evening extends AppointmentAvailability("evening", "Evening appointment (after 5pm)")
  case object Day extends AppointmentAvailability("day", "Generally available during the day")
  case object Either extends AppointmentAvailability("either", "Evening or day")

  val values: immutable.IndexedSeq[AppointmentAvailability] = findValues

  object Formatter extends Formatter[AppointmentAvailability] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], AppointmentAvailability] = {
      data.get(key).map(id =>
        values.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.appointmentAvailability.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "error.appointmentAvailability.missing"))))
    }

    override def unbind(key: String, value: AppointmentAvailability): Map[String, String] = Map(
      key -> value.id
    )
  }
}
