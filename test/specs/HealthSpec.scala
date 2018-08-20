package specs

import domain.Fixtures.users
import helpers.FakeRequestMethods._
import play.api.http.MimeTypes
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import warwick.sso.User

import scala.concurrent.Future

class HealthSpec extends BaseSpec {

  "The application" should {

    "send 404 on a bad request" in {
      status(req("/service/boom").get()) mustEqual NOT_FOUND
    }

    "respond to GTG" in {
      val res = req("/service/gtg").get()
      status(res) mustBe OK
      contentType(res) mustBe Some(MimeTypes.TEXT)
      contentAsString(res) mustBe """"OK""""
    }

  }

}
