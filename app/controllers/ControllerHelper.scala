package controllers

import helpers.Json._
import helpers.ServiceResults.ServiceError
import play.api.libs.json.Json
import play.api.mvc.{Request, Results}
import system.Logging
import warwick.sso.AuthenticatedRequest

import scala.language.implicitConversions

trait ControllerHelper extends Results with Logging {
  self: BaseController =>

  def currentUser()(implicit request: AuthenticatedRequest[_]) = request.context.user.get

  def showErrors(errors: Seq[_ <: ServiceError])(implicit request: Request[_]) =
    render {
      case Accepts.Json() => BadRequest(Json.toJson(JsonClientError(status = "bad_request", errors = errors.map(_.message))))
      case _ => BadRequest(views.html.errors.multiple(errors))
    }
}
