package helpers

import play.api.libs.json._

object ServiceResults {
  trait ServiceError extends Serializable {
    def message: String
    def cause: Option[Throwable] = None
  }

  implicit def serviceErrorFormat: Format[ServiceError] = new Format[ServiceError] {
    def reads(js: JsValue): JsResult[ServiceError] =
      js.validate[String].map { m => new ServiceError { override val message: String = m }}

    def writes(a: ServiceError): JsValue = JsString(a.message)
  }

  type ServiceResult[A] = Either[List[_ <: ServiceError], A]

  def throwableToError[A](msg: Option[String] = None)(block: => A): ServiceResult[A] =
    try {
      Right(block)
    } catch {
      case ex: Throwable => Left(List(
        new ServiceError {
          val message: String = msg.getOrElse(ex.getMessage)
          override val cause = Some(ex)
        }
      ))
    }

  def sequence[A](in: Seq[ServiceResult[A]]): ServiceResult[Seq[A]] = in.partition(_.isLeft) match {
    case (Nil, results) => Right(results.collect { case Right(x) => x })
    case (errors, _) => Left(errors.toList.collect { case Left(x) => x }.flatten)
  }
}
