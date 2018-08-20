package specs


import domain.Fixtures.users
import play.api.mvc.{Headers, Result}
import play.api.test.Helpers._
import play.api.test._
import warwick.sso.User

import scala.concurrent.Future

class HomeSpec extends BaseSpec {

  "The home page" should {

    "render for a student" in {
      val home = req("/").forUser(users.studentNewVisitor).get()

      status(home) mustEqual OK
      contentType(home).get mustEqual "text/html"
      contentAsString(home) must include("Make an enquiry")
    }

    "reject a user without a University ID" in {
      val home = req("/").forUser(users.noUniId).get()

      status(home) mustEqual PRECONDITION_FAILED
      contentAsString(home) must include("University ID is required")
    }

    "redirect away an anonymous user" in {
      val res = req("/").get()

      status(res) mustBe SEE_OTHER
      header("Location", res).get must startWith ("https://sso.example.com/login")
    }
  }
}
