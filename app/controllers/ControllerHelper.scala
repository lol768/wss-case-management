package controllers

import helpers.Json._
import helpers.ServiceResults.Implicits._
import helpers.ServiceResults.{ServiceError, ServiceResult}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Result, Results}
import warwick.core.Logging
import warwick.sso.{AuthenticatedRequest, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ControllerHelper extends Results with Logging {
  self: BaseController =>

  def currentUser()(implicit request: AuthenticatedRequest[_]): User = request.context.user.get

  def showErrors(errors: Seq[_ <: ServiceError])(implicit request: RequestHeader): Result =
    render {
      case Accepts.Json() => BadRequest(Json.toJson(JsonClientError(status = "bad_request", errors = errors.map(_.message))))
      case _ => BadRequest(views.html.errors.multiple(errors))
    }

  implicit class FutureServiceResultControllerOps[A](val future: Future[ServiceResult[A]]) {
    def successMap(fn: A => Result)(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.map { result =>
        result.fold(showErrors, fn)
      }

    def successFlatMap(fn: A => Future[Result])(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.flatMap { result =>
        result.fold(
          e => Future.successful(showErrors(e)),
          fn
        )
      }
  }

  implicit def futureServiceResultOps[A](future: Future[ServiceResult[A]]): FutureServiceResultOps[A] =
    new FutureServiceResultOps[A](future)
}
