package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json._
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

object PreviousCounselling {
  implicit val previousCounsellingFormatter: Format[PreviousCounselling] = Format(
    JsPath.read[String].map[PreviousCounselling](id => PreviousCounsellings.values.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown previous counselling id $id"))),
    (o: PreviousCounselling) => JsString(o.id)
  )
}

sealed abstract class PreviousCounselling(
  val id: String,
  val description: String
) extends IdAndDescription with EnumEntry

object PreviousCounsellings extends Enum[PreviousCounselling] {
  case object None extends PreviousCounselling("none", "None")
  case object CounsellingElsewhere extends PreviousCounselling("counsellingElsewhere", "Counselling elsewhere")
  case object CounsellingUniversity extends PreviousCounselling("counsellingUniversity", "Counselling at the University")
  case object CPN extends PreviousCounselling("cpn", "Community Psychiatric Nurse")
  case object Psychiatrist extends PreviousCounselling("psychiatrist", "Psychiatrist")
  case object Psychologist extends PreviousCounselling("psychologist", "Psychologist")
  case object MHSUniversity extends PreviousCounselling("mhsUniversity", "Mental Health support at the University")
  case object MHSElsewhere extends PreviousCounselling("mhsElsewhere", "Mental Health support elsewhere")
  case object WellbeingUniversity extends PreviousCounselling("wellbeingUniversity", "Wellbeing Advisor at the University")
  case object WellbeingElsewhere extends PreviousCounselling("wellbeingElsewhere", "Wellbeing Advisor elsewhere")
  case object NHS extends PreviousCounselling("nhs", "(NHS) Crisis Resolution and Home Treatment team")
  case object DisabilityUniversity extends PreviousCounselling("disabilityUniversity", "Disability support at the University")
  case object DisabilityElsewhere extends PreviousCounselling("disabilityElsewhere", "Disability support elsewhere")

  val values: immutable.IndexedSeq[PreviousCounselling] = findValues

  object Formatter extends Formatter[PreviousCounselling] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], PreviousCounselling] = {
      data.get(key).map(id =>
        values.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.previousCounselling.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: PreviousCounselling): Map[String, String] = Map(
      key -> value.id
    )
  }
}
