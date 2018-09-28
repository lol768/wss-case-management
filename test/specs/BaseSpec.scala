package specs

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import helpers.FakeRequestMethods._
import helpers.{OneAppPerSuite, TestApplications}
import org.dom4j.io.DOMReader
import org.dom4j.{Document, Element}
import org.htmlcleaner.{DomSerializer, HtmlCleaner}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.ac.warwick.util.web.Uri
import warwick.sso.User
import play.api.inject._
import slick.basic.{BasicProfile, DatabaseConfig}

import scala.collection.JavaConverters._
import scala.concurrent.Future

abstract class BaseSpec extends PlaySpec with MockitoSugar with OneAppPerSuite with HtmlNavigation {

  val postgres = EmbeddedPostgres.builder().start()

  override def fakeApplicationBuilder: GuiceApplicationBuilder =
    TestApplications.fullBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.PostgresProfile$",
          "slick.dbs.default.db.driver" -> "org.postgresql.Driver",
          "slick.dbs.default.db.url" -> postgres.getJdbcUrl("postgres", "postgres"),
          "slick.dbs.default.db.user" -> "postgres",
          "slick.dbs.default.db.password" -> "postgres",
          "slick.dbs.default.db.numThreads" -> 10
      )

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
