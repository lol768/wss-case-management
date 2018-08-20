import controllers.RequestContext
import helpers.Json._
import javax.inject.{Inject, Singleton}
import play.api.http.{HttpErrorHandler, Status}
import play.api.libs.json.Json
import play.api.mvc.{AcceptExtractors, Rendering, RequestHeader, Results}
import play.api.{Configuration, Environment}
import system.Logging
import warwick.sso.SSOClient

import scala.concurrent.Future

/**
  * Serves custom error views.
  */
@Singleton
class ErrorHandler @Inject()(
  environment: Environment,
  sso: SSOClient,
  configuration: Configuration
) extends HttpErrorHandler with Results with Status with Logging with Rendering with AcceptExtractors {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    implicit val context = RequestContext.authenticated(sso, request, Nil, configuration)

    Future.successful(
      statusCode match {
        case NOT_FOUND => render {
          case Accepts.Json() => NotFound(Json.toJson(JsonClientError(status = "not_found", errors = Seq(message))))
          case _ => NotFound(views.html.errors.notFound())
        }(request)
        case _ => render {
          case Accepts.Json() => Status(statusCode)(Json.toJson(JsonClientError(status = statusCode.toString, errors = Seq(message))))
          case _ => Status(statusCode)(views.html.errors.clientError(statusCode, message))
        }(request)
      }
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    implicit val context = RequestContext.authenticated(sso, request, Nil, configuration)

    logger.error("Internal Server Error", exception)
    Future.successful(
      render {
        case Accepts.Json() => InternalServerError(Json.toJson(JsonClientError(status = "internal_server_error", errors = Seq(exception.getMessage))))
        case _ => InternalServerError(views.html.errors.serverError(exception, environment.mode))
      }(request)
    )
  }

}
