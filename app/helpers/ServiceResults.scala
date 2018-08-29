package helpers

import play.api.Logger
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

object ServiceResults {
  trait ServiceError extends Serializable {
    def message: String
    def cause: Option[Throwable] = None
  }

  case class ServiceErrorImpl(message: String, override val cause: Option[Throwable]) extends ServiceError

  object ServiceError {
    def apply(msg: String, cause: Throwable): ServiceError = ServiceErrorImpl(msg, Option(cause))
    def apply(msg: String): ServiceError = ServiceErrorImpl(msg, None)
  }

  implicit def serviceErrorFormat: Format[ServiceError] = new Format[ServiceError] {
    def reads(js: JsValue): JsResult[ServiceError] =
      js.validate[String].map(ServiceError.apply)

    def writes(a: ServiceError): JsValue = JsString(a.message)
  }

  type ServiceResult[A] = Either[List[_ <: ServiceError], A]

  /**
    * Converts exception-throwing code into a ServiceResult, catching any exceptions
    * into a Left(ServiceError) and any result into a Right(result).
    */
  def catchAsServiceError[A](msg: Option[String] = None)(block: => A): ServiceResult[A] =
    try {
      Right(block)
    } catch {
      case ex: Throwable => exceptionToServiceResult(ex, msg)
    }

  def exceptionToServiceResult[A](ex: Throwable, msg: Option[String] = None): ServiceResult[A] =
    Left(List(ServiceError(msg.getOrElse(ex.getMessage), ex)))

  def logErrors[A](
    result: ServiceResult[A],
    logger: Logger,
    fallback: A,
    message: List[_ <: ServiceError] => Option[String] = _ => None
  ): A  = {
    result.fold(
      e => {
        val msg = message(e).getOrElse(e.head.message)
        e.headOption.flatMap(_.cause).fold(logger.error(msg))(t => logger.error(msg, t))
        fallback
      },
      success => success
    )
  }

  def sequence[A](in: Seq[ServiceResult[A]]): ServiceResult[Seq[A]] = in.partition(_.isLeft) match {
    case (Nil, results) => Right(results.collect { case Right(x) => x })
    case (errors, _) => Left(errors.toList.collect { case Left(x) => x }.flatten)
  }

  def futureSequence[A](in: Seq[Future[ServiceResult[A]]])(implicit executionContext: ExecutionContext): Future[ServiceResult[Seq[A]]] =
    Future.sequence(in).map(a => sequence(a))

  def zip[T1, T2](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2)]] =
    _1.zip(_2).map {
      case (Right(r1), Right(r2)) => Right((r1, r2))
      case (s1, s2) => Left(List(s1, s2).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3)]] =
    zip(_1, _2).zip(_3).map {
      case (Right((r1, r2)), Right(r3)) => Right((r1, r2, r3))
      case (s, s3) => Left(List(s, s3).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4)]] =
    zip(_1, _2, _3).zip(_4).map {
      case (Right((r1, r2, r3)), Right(r4)) => Right((r1, r2, r3, r4))
      case (s, s4) => Left(List(s, s4).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5)]] =
    zip(_1, _2, _3, _4).zip(_5).map {
      case (Right((r1, r2, r3, r4)), Right(r5)) => Right((r1, r2, r3, r4, r5))
      case (s, s5) => Left(List(s, s5).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5, T6](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6)]] =
    zip(_1, _2, _3, _4, _5).zip(_6).map {
      case (Right((r1, r2, r3, r4, r5)), Right(r6)) => Right((r1, r2, r3, r4, r5, r6))
      case (s, s6) => Left(List(s, s6).collect { case Left(x) => x }.flatten)
    }
}
