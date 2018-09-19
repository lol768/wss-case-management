package specs

import play.api.http.MimeTypes
import play.api.test.Helpers._

class HealthSpec extends BaseSpec {

  "The application" should {

    "redirect on a bad request if the user isn't authenticated" in {
      status(req("/service/boom").get()) mustEqual SEE_OTHER
    }

    "respond to GTG" in {
      val res = req("/service/gtg").get()
      status(res) mustBe OK
      contentType(res) mustBe Some(MimeTypes.TEXT)
      contentAsString(res) mustBe """"OK""""
    }

  }

}
