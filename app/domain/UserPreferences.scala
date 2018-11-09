package domain

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class UserPreferences(
  calendarView: String,
  office365Enabled: Boolean
)

object UserPreferences {
  val default = UserPreferences(
    calendarView = "agendaWeek",
    office365Enabled = false
  )

  private val writer = Json.writes[UserPreferences]
  private val reader = (
    (JsPath \ "calendarView").readNullable[String] and
    (JsPath \ "office365Enabled").readNullable[Boolean]
  )((calendarViewOption, office365EnabledOption) => UserPreferences(
    calendarView = calendarViewOption.getOrElse(UserPreferences.default.calendarView),
    office365Enabled = office365EnabledOption.getOrElse(UserPreferences.default.office365Enabled)
  ))

  implicit val formatter: Format[UserPreferences] = new Format[UserPreferences] {
    override def writes(o: UserPreferences): JsValue = writer.writes(o)

    override def reads(json: JsValue): JsResult[UserPreferences] = reader.reads(json)
  }
}
