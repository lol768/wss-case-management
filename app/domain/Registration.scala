package domain

import java.time.OffsetDateTime

import play.api.libs.json.{Format, JsValue, Json, Writes}
import warwick.sso.UniversityID

case class Registration(
  universityID: UniversityID,
  updatedDate: OffsetDateTime,
  data: Option[RegistrationData],
  lastInvited: OffsetDateTime
)

object RegistrationData {
  import Disability._
  import Medication._
  import RegistrationReferral._
  implicit val formatter: Format[RegistrationData] = Json.format[RegistrationData]
}

case class RegistrationData(
  gp: String,
  tutor: String,
  disabilities: Set[Disability],
  medications: Set[Medication],
  appointmentAdjustments: String,
  referrals: Set[RegistrationReferral],
  consentPrivacyStatement: Option[Boolean]
)

object RegistrationDataHistory {

  val writer: Writes[RegistrationDataHistory] = (r: RegistrationDataHistory) => Json.obj(
    "gp" -> toJson(r.gp),
    "tutor" -> toJson(r.tutor),
    "disabilities" -> toJson(r.disabilities)(Writes.set(IdAndDescription.writer)),
    "medications" -> toJson(r.medications)(Writes.set(IdAndDescription.writer)),
    "appointmentAdjustments" -> toJson(r.appointmentAdjustments),
    "referrals" -> toJson(r.referrals)(Writes.set(IdAndDescription.writer)),
    "consentPrivacyStatement" -> toJson(r.consentPrivacyStatement)
  )

  def apply(history: Seq[(RegistrationData, OffsetDateTime)]): RegistrationDataHistory = RegistrationDataHistory(
    gp = flatten(history.map { case (result, ts) => (result.gp, ts) }.toList),
    tutor = flatten(history.map { case (result, ts) => (result.tutor, ts) }.toList),
    disabilities = flatten(history.map { case (result, ts) => (result.disabilities, ts) }.toList),
    medications = flatten(history.map { case (result, ts) => (result.medications, ts) }.toList),
    appointmentAdjustments = flatten(history.map { case (result, ts) => (result.appointmentAdjustments, ts) }.toList),
    referrals = flatten(history.map { case (result, ts) => (result.referrals, ts) }.toList),
    consentPrivacyStatement = flatten(history.map { case (result, ts) => (result.consentPrivacyStatement, ts) }.toList),
  )

  private def flatten[A](items: List[(A, OffsetDateTime)]): Seq[(A, OffsetDateTime)] = (items match {
    case Nil => Nil
    case head :: Nil => Seq(head)
    case head :: tail => tail.foldLeft(Seq(head)) { (foldedItems, item) =>
      if (foldedItems.last._1 != item._1) {
        foldedItems ++ Seq(item)
      } else {
        foldedItems
      }
    }
  }).reverse

  private def toJson[A](items: Seq[(A, OffsetDateTime)])(implicit itemWriter: Writes[A]): JsValue =
    Json.toJson(items.map { case (item, ts) => Json.obj(
      "value" -> Json.toJson(item),
      "version" -> ts
    ) })

}

case class RegistrationDataHistory(
  gp: Seq[(String, OffsetDateTime)],
  tutor: Seq[(String, OffsetDateTime)],
  disabilities: Seq[(Set[Disability], OffsetDateTime)],
  medications: Seq[(Set[Medication], OffsetDateTime)],
  appointmentAdjustments: Seq[(String, OffsetDateTime)],
  referrals: Seq[(Set[RegistrationReferral], OffsetDateTime)],
  consentPrivacyStatement: Seq[(Option[Boolean], OffsetDateTime)]
)