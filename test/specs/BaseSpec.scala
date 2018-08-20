package specs

import helpers.FakeRequestMethods._
import helpers.OneAppPerSuite
import org.dom4j.io.DOMReader
import org.dom4j.{Document, Element}
import org.htmlcleaner.{DomSerializer, HtmlCleaner}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.ac.warwick.util.web.Uri
import warwick.sso.User

import scala.collection.JavaConverters._
import scala.concurrent.Future

abstract class BaseSpec extends PlaySpec with MockitoSugar with OneAppPerSuite with HtmlNavigation {

  // This might be a bad idea. Experimenting with ways to make all the specs
  // be readable and not too repetitive.
  case class req(path: String, user: Option[User] = None) {
    def forUser(u: User) = copy(user = Some(u))
    def get(): Future[Result] = {
      val plainReq = FakeRequest(GET, path)
      val req = user.map(plainReq.withUser(_)).getOrElse(plainReq)
      route(app, req).getOrElse {
        fail(s"No match found for $path")
      }
    }
  }

}
