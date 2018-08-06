package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json._
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

object CounsellingType {
  implicit val counsellingTypeFormatter: Format[CounsellingType] = Format(
    JsPath.read[String].map[CounsellingType](id => CounsellingTypes.values.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown counselling type id $id"))),
    (o: CounsellingType) => JsString(o.id)
  )
}

sealed abstract class CounsellingType(
  val id: String,
  val description: String
) extends IdAndDescription with EnumEntry

object CounsellingTypes extends Enum[CounsellingType] {
  case object Email extends CounsellingType("email", "Email Counselling")
  case object FaceToFace extends CounsellingType("faceToFace", "Face to face Counselling")
  case object Group extends CounsellingType("group", "Group Therapy")
  case object NotSure extends CounsellingType("notSure", "Not sure")

  val values: immutable.IndexedSeq[CounsellingType] = findValues

  object Formatter extends Formatter[CounsellingType] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], CounsellingType] = {
      data.get(key).map(id =>
        values.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.counsellingType.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: CounsellingType): Map[String, String] = Map(
      key -> value.id
    )
  }
}
