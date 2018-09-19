package controllers

import helpers.Json._
import helpers.ServiceResults.{ServiceError, ServiceResult}
import play.api.libs.json.Json
import play.api.mvc.{Request, RequestHeader, Result, Results}
import system.Logging
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ControllerHelper extends Results with Logging {
  self: BaseController =>

  def currentUser()(implicit request: AuthenticatedRequest[_]) = request.context.user.get

  def showErrors(errors: Seq[_ <: ServiceError])(implicit request: RequestHeader): Result =
    render {
      case Accepts.Json() => BadRequest(Json.toJson(JsonClientError(status = "bad_request", errors = errors.map(_.message))))
      case _ => BadRequest(views.html.errors.multiple(errors))
    }

  implicit class EnhancedFutureServiceError[A](val future: Future[ServiceResult[A]]) {
    def successMap(fn: A => Result)(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.map { result =>
        result.fold(showErrors, fn)
      }

    def successMapTo[B](fn: A => B)(implicit ec: ExecutionContext): Future[ServiceResult[B]] =
      future.map { result =>
        result.fold(Left.apply, a => Right(fn(a)))
      }

    def successFlatMap(fn: A => Future[Result])(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.flatMap { result =>
        result.fold(
          e => Future.successful(showErrors(e)),
          fn
        )
      }

    def successFlatMapTo[B](fn: A => Future[ServiceResult[B]])(implicit ec: ExecutionContext): Future[ServiceResult[B]] =
      future.flatMap { result =>
        result.fold(
          e => Future.successful(Left(e)),
          fn
        )
      }
  }
}
