package helpers

import helpers.ServiceResults.ServiceError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class ServiceResultsTest extends PlaySpec with MockitoSugar with ScalaFutures {

  case class CustomServiceError(message: String) extends ServiceError

  "ServiceResults.sequence" should {
    "collect successful results" in {
      val f1 = Future.successful(Right(true))
      val f2 = Future.successful(Right(false))
      val f3 = Future.successful(Right(true))

      Future.sequence(Seq(f1, f2, f3)).map(ServiceResults.sequence).futureValue mustBe Right(Seq(true, false, true))
    }

    "collect errors" in {
      val f1 = Future.successful(Right(true))
      val f2 = Future.successful(Left(List(CustomServiceError("failed"))))
      val f3 = Future.successful(Right(true))

      Future.sequence(Seq(f1, f2, f3)).map(ServiceResults.sequence).futureValue mustBe Left(List(CustomServiceError("failed")))
    }

    "propagate Future.failed" in {
      val ex = new RuntimeException

      val f1 = Future.successful(Right(true))
      val f2 = Future.failed(ex)
      val f3 = Future.successful(Right(true))

      Try(Future.sequence(Seq(f1, f2, f3)).map(ServiceResults.sequence).futureValue).isFailure mustBe true
    }
  }

}
