package helpers

import play.api.libs.json.{JsString, Writes, Json => PlayJson}
import warwick.sso.Usercode

object Json {
  case class JsonOperationResult(success: Boolean = false)
  implicit val writesJsonOperationResult: Writes[JsonOperationResult] = PlayJson.writes[JsonOperationResult]

  case class JsonClientError(status: String, errors: Seq[String] = Nil, success: Boolean = false)
  implicit val writesJsonClientError: Writes[JsonClientError] = PlayJson.writes[JsonClientError]

  implicit val writesSSOUsercode: Writes[Usercode] = usercode => JsString(usercode.string)
}
