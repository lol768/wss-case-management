package controllers

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.Json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._
import play.api.mvc.Results

/**
  * Builder for API response objects.
  */
object API {

  def badRequestJson(formWithErrors: Form[_])(implicit messages: Messages) =
    Results.BadRequest(Json.toJson(API.Failure[JsObject]("bad_request",
      formWithErrors.errors.map(error => API.Error(error.getClass.getSimpleName, error.format, error.message))
    )))

  sealed abstract class Response[A: Reads : Writes](val success: Boolean, status: String) {
    implicit def reads: Reads[Response[A]] = Response.reads[A]

    implicit def writes: Writes[Response[A]] = Response.writes[A]

    // Maybe this is useful, if you like using Either
    def either: Either[Failure[A], Success[A]]
  }

  case class Error(id: String, message: String, messageKey: String = null)

  case class Success[A: Reads : Writes](status: String = "ok", data: A) extends Response[A](true, status) {
    def either = Right(this)
  }

  case class Failure[A: Reads : Writes](status: String, errors: Seq[Error]) extends Response[A](false, status) {
    def either = Left(this)
  }

  object Error {
    implicit val format: OFormat[Error] = Json.format[Error]

    def fromJsError(jsError: JsError): Seq[Error] = JsError.toFlatForm(jsError).map {
      case (field, errors) =>
        val propertyName = field.substring(4) // Remove 'obj.' from start of field name
        Error(
          s"invalid-$propertyName",
          errors.flatMap(_.messages).mkString(", ")
        )
    }

  }

  object Response {
    implicit def reads[A: Reads : Writes]: Reads[Response[A]] = (json: JsValue) => {
      val status = (json \ "status").validate[String]
      (json \ "success").validate[Boolean].flatMap { success =>
        if (success) {
          val data = (json \ "data").validate[A]
          (status and data) (Success.apply[A] _)
        } else {
          val errors = (json \ "errors").validate[Seq[Error]]
          (status and errors) (Failure.apply[A] _)
        }
      }
    }

    implicit def writes[A: Reads : Writes]: Writes[Response[A]] = {
      case Success(status, data: A @unchecked) => Json.obj(
        "success" -> true,
        "status" -> status,
        "data" -> data
      )
      case Failure(status, errors) => Json.obj(
        "success" -> false,
        "status" -> status,
        "errors" -> errors
      )
    }
  }

}
