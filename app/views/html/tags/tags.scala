package views.html

import domain.SitsProfile
import warwick.sso.UniversityID

package object tags {

  def clientFullName(e: Either[UniversityID, SitsProfile]): String =
    e.fold(_.string, _.fullName)

  def p(number: Int, singular: String)(plural: String = s"${singular}s", one: String = "1", zero: String = "0", showNumber: Boolean = true): String = {
    val word = if (number == 1) {
      singular
    } else {
      plural
    }
    if (showNumber) {
      val num = if (number == 1) {
        one
      } else if (number == 0) {
        zero
      } else {
        number.toString
      }
      s"$num $word"
    } else {
      word
    }
  }


}
