package views.html

import domain.Creator

package object tags {

  def creatorAndTeam(creator: Creator): String = {
    val teams = if (creator.teams.nonEmpty) s", ${creator.teams.map(_.name).mkString(", ")}" else ""
    s" ${creator.member.safeFullName}$teams"
  }

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
