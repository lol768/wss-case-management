package helpers

import play.api.Logger
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

  /**
    * Extra operations on (Future) ServiceResults:
    *
    * {{{import helpers.ServiceResults.Implicits._}}}
    */
  object Implicits {

    implicit class FutureServiceResultOps[A](val future: Future[ServiceResult[A]]) {
      /**
        * Maps a successful service result to another value - other outcomes are unchanged.
        */
      def successMapTo[B](fn: A => B)(implicit ec: ExecutionContext): Future[ServiceResult[B]] =
        future.map { result =>
          result.fold(Left.apply, a => Right(fn(a)))
        }

      /**
        * Flatmaps a successful service result to a future of another value - other outcomes are unchanged.
        */
      def successFlatMapTo[B](fn: A => Future[ServiceResult[B]])(implicit ec: ExecutionContext): Future[ServiceResult[B]] =
        future.flatMap { result =>
          result.fold(
            e => Future.successful(Left(e)),
            fn
          )
        }
    }

  }

  implicit def serviceErrorFormat: Format[ServiceError] = new Format[ServiceError] {
    def reads(js: JsValue): JsResult[ServiceError] =
      js.validate[String].map(ServiceError.apply)

    def writes(a: ServiceError): JsValue = JsString(a.message)
  }

  type ServiceResult[A] = Either[List[_ <: ServiceError], A]

  def fromTry[A](t: Try[A]): ServiceResult[A] = t.fold(
    e => ServiceResults.exceptionToServiceResult(e),
    r => Right(r)
  )

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

  def error[A](msg: String): ServiceResult[A] =
    Left(List(ServiceError(msg)))

  def logErrors[A](
    result: ServiceResult[A],
    logger: Logger,
    fallback: A,
    message: List[_ <: ServiceError] => Option[String] = _ => None
  ): ServiceResult[A]  = {
    result.fold(
      e => {
        val msg = message(e).getOrElse(e.head.message)
        e.headOption.flatMap(_.cause).fold(logger.error(msg))(t => logger.error(msg, t))
        Right(fallback)
      },
      success => Right(success)
    )
  }

  def success[A](result: A): ServiceResult[A] = Right(result)

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

  def zip[T1, T2, T3, T4, T5, T6, T7](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]], _7: Future[ServiceResult[T7]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6, T7)]] =
    zip(_1, _2, _3, _4, _5, _6).zip(_7).map {
      case (Right((r1, r2, r3, r4, r5, r6)), Right(r7)) => Right((r1, r2, r3, r4, r5, r6, r7))
      case (s, s7) => Left(List(s, s7).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5, T6, T7, T8](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]], _7: Future[ServiceResult[T7]], _8: Future[ServiceResult[T8]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6, T7, T8)]] =
    zip(_1, _2, _3, _4, _5, _6, _7).zip(_8).map {
      case (Right((r1, r2, r3, r4, r5, r6, r7)), Right(r8)) => Right((r1, r2, r3, r4, r5, r6, r7, r8))
      case (s, s8) => Left(List(s, s8).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5, T6, T7, T8, T9](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]], _7: Future[ServiceResult[T7]], _8: Future[ServiceResult[T8]], _9: Future[ServiceResult[T9]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6, T7, T8, T9)]] =
    zip(_1, _2, _3, _4, _5, _6, _7, _8).zip(_9).map {
      case (Right((r1, r2, r3, r4, r5, r6, r7, r8)), Right(r9)) => Right((r1, r2, r3, r4, r5, r6, r7, r8, r9))
      case (s, s9) => Left(List(s, s9).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]], _7: Future[ServiceResult[T7]], _8: Future[ServiceResult[T8]], _9: Future[ServiceResult[T9]], _10: Future[ServiceResult[T10]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)]] =
    zip(_1, _2, _3, _4, _5, _6, _7, _8, _9).zip(_10).map {
      case (Right((r1, r2, r3, r4, r5, r6, r7, r8, r9)), Right(r10)) => Right((r1, r2, r3, r4, r5, r6, r7, r8, r9, r10))
      case (s, s10) => Left(List(s, s10).collect { case Left(x) => x }.flatten)
    }

  def zip[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](_1: Future[ServiceResult[T1]], _2: Future[ServiceResult[T2]], _3: Future[ServiceResult[T3]], _4: Future[ServiceResult[T4]], _5: Future[ServiceResult[T5]], _6: Future[ServiceResult[T6]], _7: Future[ServiceResult[T7]], _8: Future[ServiceResult[T8]], _9: Future[ServiceResult[T9]], _10: Future[ServiceResult[T10]], _11: Future[ServiceResult[T11]])(implicit executionContext: ExecutionContext): Future[ServiceResult[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)]] =
    zip(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10).zip(_11).map {
      case (Right((r1, r2, r3, r4, r5, r6, r7, r8, r9, r10)), Right(r11)) => Right((r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11))
      case (s, s11) => Left(List(s, s11).collect { case Left(x) => x }.flatten)
    }
}
