package specs

import helpers.FakeRequestMethods._
import helpers.{FutureServiceMixins, OneAppPerSuite}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.User

import scala.concurrent.Future

/**
  * A spec designed for testing most of the app stack, calling
  * controllers as a browser would (though without using HTTP).
  */
abstract class BaseSpec
  extends PlaySpec
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with OneAppPerSuite
    with HtmlNavigation
    with FutureServiceMixins {

  implicit def timingContext: TimingContext = TimingContext.none
  implicit def auditLogContext: AuditLogContext = AuditLogContext.empty()

  // This might be a bad idea. Experimenting with ways to make all the specs
  // be readable and not too repetitive.
  case class req(path: String, user: Option[User] = None) {
    def forUser(u: User) = copy(user = Option(u))
    def get(): Future[Result] = {
      val plainReq = FakeRequest(GET, path)
      val req = user.map(plainReq.withUser(_)).getOrElse(plainReq)
      route(app, req).getOrElse {
        fail(s"No match found for $path")
      }
    }
  }

  implicit class FutureMvcResultOps(r: Future[Result]) {

    def mustBeRedirectToLogin = {
      withClue("Expecting Location header pointing at sso.example.com") {
        redirectLocation(r).value must startWith("https://sso.example.com/login")
      }
    }


  }

}
