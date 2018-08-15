package helpers

import java.io.IOException

import play.api.libs.ws.WSResponse
import uk.ac.warwick.sso.client.trusted.TrustedApplication.{HEADER_ERROR_CODE, HEADER_ERROR_MESSAGE}

object TrustedAppsHelper {
  def validateResponse(url:String, response: WSResponse): WSResponse = {
    TrustedAppsError.fromWSResponse(response).foreach(e => throw e)
    if (response.status >= 400) {
      throw new IOException(s"Response code ${response.status} from $url")
    }
    response
  }
}

class TrustedAppsError(code: String, message: Option[String]) extends RuntimeException(message.getOrElse(code))

object TrustedAppsError {
  def fromWSResponse(res: WSResponse): Option[TrustedAppsError] =
    res.header(HEADER_ERROR_CODE)
      .map(new TrustedAppsError(_, res.header(HEADER_ERROR_MESSAGE)))
}
