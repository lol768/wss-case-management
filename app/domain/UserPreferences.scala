package domain

import play.api.libs.json.{Format, Json}

case class UserPreferences(
  calendarView: String
)

object UserPreferences {
  val default = UserPreferences(
    calendarView = "agendaWeek"
  )

  implicit val formatter: Format[UserPreferences] = Json.format[UserPreferences]
}
