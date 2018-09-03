package system

import play.filters.csrf.CSRF.Token
import play.filters.csrf.CSRFConfig
import play.twirl.api._

class CSRFPageHelper (csrfConfig: CSRFConfig, _token: Option[Token]) {

  def token: Option[Token] = _token

  def metaElementToken(): Html = {
    // If the token is missing, there's not much we can do
    // Avoid an exception by just using some stand-in text
    html"""<meta name="_csrf" content="${HtmlFormat.escape(_token.fold("Missing")(_.value))}"/>"""
  }

  def metaElementHeader(): Html = {
    html"""<meta name="_csrf_header" content="${csrfConfig.headerName}"/>"""
  }

  def headerName(): String = {
    csrfConfig.headerName
  }

  // Provide a convenience method which doesn't require Some(token)an implicit RequestHeader in
  // each view that we wish to have a CSRF field in.
  def formField(): Html = {
    html"""<input type="hidden" name="${csrfConfig.tokenName}" value="${HtmlFormat.escape(_token.fold("Missing")(_.value))}">"""
  }

}
