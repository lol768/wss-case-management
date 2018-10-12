import controllers.RequestContext
import helpers.Json._
import javax.inject.{Inject, Singleton}
import play.api.Environment
import play.api.http.{HttpErrorHandler, Status}
import play.api.libs.json.Json
import play.api.mvc._
import system.ImplicitRequestContext
import warwick.core.Logging

import scala.concurrent.Future

/**
  * Serves custom error views.
  */
@Singleton
class ErrorHandler @Inject()(
  environment: Environment,
) extends HttpErrorHandler with Results with Status with Logging with Rendering with AcceptExtractors with ImplicitRequestContext {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val context: RequestContext = requestContext(request)

    // If we don't have a valid user, force login anyway
    // (don't give unauthenticated users information about what's a 404 and what isn't)
    if (context.user.isEmpty)
      Future.successful(Redirect(context.loginUrl))
    else
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

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val requestHeader: RequestHeader = request

    logger.error("Internal Server Error", exception)
    Future.successful(
      render {
        case Accepts.Json() => InternalServerError(Json.toJson(JsonClientError(status = "internal_server_error", errors = Seq(exception.getMessage))))
        case _ => InternalServerError(views.html.errors.serverError(exception, environment.mode))
      }(request)
    )
  }

}
