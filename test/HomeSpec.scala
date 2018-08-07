
import Fixtures.users
import helpers.FakeRequestMethods._
import helpers.OneAppPerSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.HeaderNames
import play.api.test.Helpers._
import play.api.test._

class HomeSpec extends PlaySpec with MockitoSugar with OneAppPerSuite {

  "The home page" should {
    "send 404 on a bad request" in {
      status(route(app, FakeRequest(GET, "/service/boom")).get) mustEqual NOT_FOUND
    }

    "render for a student" in {
      val home = route(app, FakeRequest(GET, "/").withUser(users.studentNewVisitor)).get

      status(home) mustEqual OK
      contentType(home).get mustEqual "text/html"
      contentAsString(home) must include("Enquiries")
    }

    "reject a user without a University ID" in {
      val home = route(app, FakeRequest(GET, "/").withUser(users.noUniId)).get

      status(home) mustEqual PRECONDITION_FAILED
      contentAsString(home) must include("University ID is required")
    }
  }
}
