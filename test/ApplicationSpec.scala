
import helpers.OneAppPerSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.HeaderNames
import play.api.test.Helpers._
import play.api.test._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with MockitoSugar with OneAppPerSuite {

  "Application" should {
    "send 404 on a bad request" in {
      status(route(app, FakeRequest(GET, "/service/boom")).get) mustEqual NOT_FOUND
    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/").withHeaders(HeaderNames.ACCEPT -> "text/html")).get

      status(home) mustEqual OK
      contentType(home).get mustEqual "text/html"
      contentAsString(home) must include("Lorem ipsum")
    }
  }
}
