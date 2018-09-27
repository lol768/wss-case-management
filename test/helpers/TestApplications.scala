package helpers

import org.databrary.PlayLogbackAccessModule
import org.scalatest.mockito.MockitoSugar
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, EvolutionsReader}
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.routing.Router
import play.api.{Application, Configuration, Environment}
import uk.ac.warwick.sso.client.SSOClientHandler
import warwick.accesslog.LogbackAccessModule
import routes.EmptyRouter
import warwick.sso._

import scala.reflect._

object TestApplications extends MockitoSugar {

  def simpleEnvironment = Environment.simple()

  def testConfig(environment: Environment) =
    config("test.conf", environment)

  def config(file: String, environment: Environment) =
    Configuration.load(environment, Map("config.resource" -> file))

  def minimalBuilder: GuiceApplicationBuilder =
    GuiceApplicationBuilder(loadConfiguration = e => config("minimal.conf", e))
      .in(Environment.simple())
      .disable[PlayLogbackAccessModule]
      .disable[LogbackAccessModule]

  def minimalWithRouter[R <: Router : ClassTag](config: (String, Any)*): Application =
    minimalBuilder
      .configure("play.http.router" -> classTag[R].runtimeClass.getName)
      .configure(config : _*)
      .build()

  /**
    * As minimal an Application as can be created. Use for any tests
    * where you just can't do without an Application, like something that
    * requires WSAPI which is a pain to build by hand.
    */
  def minimal(): Application =
    minimalWithRouter[EmptyRouter]()

  /**
    * As full an Application as can be created while still talking to
    * mock external services only, and an in-memory database. Used for
    * DAO tests and integration tests.
    */
  def fullBuilder(defaultUser: Option[User] = None, additionalConfiguration: Map[String, Any] = Map.empty): GuiceApplicationBuilder =
    GuiceApplicationBuilder(loadConfiguration = testConfig)
      .in(simpleEnvironment)
      .configure(additionalConfiguration)
      .bindings(
        bind[LoginContext].toInstance(new MockLoginContext(defaultUser))
      )
      .disable[PlayLogbackAccessModule]
      .overrides(
        bind[SSOClientHandler].to[warwick.sso.MockSSOClientHandler],
        bind[UserLookupService].to[warwick.sso.MockUserLookupService],
        bind[GroupService].to[warwick.sso.MockGroupService],

        // Allows putting test versions of migrations under test/resources/evolutions/default
        bind[EvolutionsReader].toInstance(new ClassLoaderEvolutionsReader)
      )

  def full(defaultUser: Option[User] = None, additionalConfiguration: Map[String, Any] = Map.empty): Application =
    fullBuilder(defaultUser, additionalConfiguration).build()
}
