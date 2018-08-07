package domain

sealed abstract class Team(val id: String, val name: String)

object Teams {
  case object Counselling extends Team("counselling", "Counselling Service")
  case object Disability extends Team("disability", "Disability Services")
  case object MentalHealth extends Team("mentalhealth", "Mental Health and Wellbeing")
  case object StudentSupport extends Team("studentsupport", "Student Support")

  val all: Seq[Team] = Seq(Counselling, Disability, MentalHealth, StudentSupport)

  def fromId(id: String): Team = id match {
    case Counselling.id => Counselling
    case Disability.id => Disability
    case MentalHealth.id => MentalHealth
    case StudentSupport.id => StudentSupport
    case _ => throw new IllegalArgumentException(s"Could not find team with id $id")
  }
}
