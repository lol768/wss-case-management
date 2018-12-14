package views.tags

import domain.{SitsProfile, UserType}

package object profiles {

  def typeAndDepartment(client: SitsProfile): String = client.userType match {
    case UserType.Student =>
      val year = client.yearOfStudy.flatMap(_.level) match {
        case Some("1") => "First year"
        case Some("2") => "Second year"
        case Some("3") => "Third year"
        case Some(level) if level.forall(_.isDigit) => s"${level}th year"
        case Some(_) if client.attendance.nonEmpty => client.attendance.get.description
        case _ => ""
      }
      (Seq(year) ++ client.group ++ Seq("student,", client.department.name)).mkString(" ")
    case _ =>
      s"${client.userType}, ${client.department.name}"
  }

  def typeAndAttendance(client: SitsProfile): String = client.userType match {
    case UserType.Student =>
      val year = client.yearOfStudy.flatMap(_.level) match {
        case Some("1") => "First year"
        case Some("2") => "Second year"
        case Some("3") => "Third year"
        case Some(level) if level.forall(_.isDigit) => s"${level}th year"
        case _ => ""
      }
      (Seq(year) ++ client.group ++ Seq("student,") ++ client.attendance.map(_.description).toSeq).mkString(" ")
    case _ =>
      client.userType.entryName
  }

}
