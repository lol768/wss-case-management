package helpers

import helpers.ServiceResults.ServiceResult
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec

import scala.concurrent.Future

trait FutureServiceMixins { self : PlaySpec with ScalaFutures =>

  implicit class FutureServiceResultOps[A](f: Future[ServiceResult[A]]) {

    // Convenient way to block on a Future[ServiceResult[_]] that you expect
    // to be successful.
    def serviceValue: A =
      f.futureValue.fold(
        e => e.flatMap(_.cause).headOption
          .map(throw _)
          .getOrElse(fail(e.map(_.message).mkString("; "))),
        identity // return success as-is
      )
  }

}
