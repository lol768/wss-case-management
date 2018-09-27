package views.tags

import domain.{SitsProfile, UserType}

package object profiles {

  def typeAndDepartment(client: SitsProfile): String = client.userType match {
    case UserType.Student =>
      val year = client.yearOfStudy.map(_.level) match {
        case Some("1") => "First year"
        case Some("2") => "Second year"
        case Some("3") => "Third year"
        case Some(level) => s"${level}th year"
        case _ => ""
      }
      s"$year ${client.group} student, ${client.department.name}"
    case _ =>
      s"${client.userType}, ${client.department.name}"
  }

}
