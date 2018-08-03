package domain

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsPath, JsString}

object RegistrationReferral {
  implicit val registrationReferralFormatter: Format[RegistrationReferral] = Format(
    JsPath.read[String].map[RegistrationReferral](id => RegistrationReferrals.all.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown referral id $id"))),
    (o: RegistrationReferral) => JsString(o.id)
  )
}

sealed abstract class RegistrationReferral(val id: String, val description: String)

object RegistrationReferrals {
  case object Myself extends RegistrationReferral("Myself", "Myself")
  case object Friend extends RegistrationReferral("Friend", "Friend")
  case object FamilyMember extends RegistrationReferral("Family member", "Family member")
  case object GP extends RegistrationReferral("GP", "GP (Medical Doctor)")
  case object IAPT extends RegistrationReferral("IAPT", "IAPT")
  case object Tutor extends RegistrationReferral("Tutor", "Personal/Academic Tutor")
  case object Residential extends RegistrationReferral("Residential", "Residential Life Team")
  case object StudentUnion extends RegistrationReferral("Student Union", "Student Union")
  case object Other extends RegistrationReferral("Other", "Other")

  def all = Seq(Myself, Friend, FamilyMember, GP, IAPT, Tutor, Residential, StudentUnion, Other)

  object Formatter extends Formatter[RegistrationReferral] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], RegistrationReferral] = {
      data.get(key).map(id =>
        all.find(_.id == id).map(Right.apply)
          .getOrElse(Left(Seq(FormError(key, "error.registrationreferral.unknown"))))
      ).getOrElse(Left(Seq(FormError(key, "missing"))))
    }

    override def unbind(key: String, value: RegistrationReferral): Map[String, String] = Map(
      key -> value.id
    )
  }
}
