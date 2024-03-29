package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json._
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

object Disability {
  implicit val disabilityFormatter: Format[Disability] = Format(
    JsPath.read[String].map[Disability](id => Disabilities.values.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown disability id $id"))),
    (o: Disability) => JsString(o.id)
  )
}

sealed abstract class Disability(
  val id: String,
  val description: String
) extends IdAndDescription with EnumEntry

object Disabilities extends Enum[Disability] {
  case object None extends Disability("none", "No disability")
  case object Social extends Disability("social", "Social/communication impairment such as Asperger's syndrome/other autistic spectrum disorder")
  case object Blind extends Disability("blind", "Blind or have a serious visual impairment uncorrected by glasses")
  case object Deaf extends Disability("deaf", "Deaf or have serious hearing impairment")
  case object LongStanding extends Disability("longStanding", "Long standing illness or health condition such as cancer, HIV, diabetes, chronic heart disease, or epilepsy")
  case object Mental extends Disability("mental", "Mental health condition, such as depression, schizophrenia or anxiety disorder")
  case object Learning extends Disability("learning", "Specific learning difficulty such as dyslexia, dyspraxia or AD(H)D")
  case object Physical extends Disability("physical", "Physical impairment or mobility issues, such as difficulty using arms or using wheelchair or crutches")
  case object Other extends Disability("other", "Disability, impairment or medical condition that is not listed above")
  case object Two extends Disability("two", "Two or more impairments and/or disabling medical conditions")

  val values: immutable.IndexedSeq[Disability] = findValues

  object Formatter extends Formatter[Disability] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Disability] = {
      data.get(key).map(id =>
        values.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.disability.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: Disability): Map[String, String] = Map(
      key -> value.id
    )
  }
}
