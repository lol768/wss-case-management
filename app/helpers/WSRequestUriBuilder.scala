package helpers

import play.api.libs.ws.WSRequest
import play.api.libs.ws.ahc.AhcWSRequest
import play.shaded.ahc.org.asynchttpclient.uri.Uri

object WSRequestUriBuilder {

  def buildUri(request: WSRequest): Uri = {
    request match {
      case r: AhcWSRequest => r.underlying.buildRequest().getUri
      case _ => throw new IllegalArgumentException("WSRequest was not AhcWSRequest")
    }
  }

}
