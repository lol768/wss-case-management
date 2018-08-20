package helpers

import org.databrary.PlayLogbackAccessModule
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.db.evolutions.{ClassLoaderEvolutionsReader, EvolutionsReader}
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Configuration, Environment}
import uk.ac.warwick.sso.client.SSOClientHandler
import warwick.accesslog.LogbackAccessModule
import warwick.sso._

import scala.util.{Success, Try}

object TestApplications extends MockitoSugar {

  def testConfig(environment: Environment) =
    config("test/test.conf", environment)

  def config(file: String, environment: Environment) =
    Configuration.load(environment, Map("config.file" -> file))

  /**
    * As minimal an Application as can be created. Use for any tests
    * where you just can't do without an Application, like something that
    * requires WSAPI which is a pain to build by hand.
    */
  def minimal() =
    GuiceApplicationBuilder(loadConfiguration = e => config("minimal.conf", e))
      .in(Environment.simple())
      .disable[PlayLogbackAccessModule]
      .disable[LogbackAccessModule]
      .build()

  /**
    * As full an Application as can be created while still talking to
    * mock external services only, and an in-memory database. Used for
    * DAO tests and integration tests.
    */
  def full(defaultUser: Option[User] = None, additionalConfiguration: Map[String, Any] = Map.empty) =
    GuiceApplicationBuilder(loadConfiguration = testConfig)
      .in(Environment.simple())
      .configure(additionalConfiguration)
      .bindings(
        bind[LoginContext].toInstance(new MockLoginContext(defaultUser))
      )
      .disable[PlayLogbackAccessModule]
      .overrides(
        bind[SSOClientHandler].to[warwick.sso.MockSSOClientHandler],
        bind[UserLookupService].to(mock[UserLookupService]),
        bind[GroupService].to[warwick.sso.MockGroupService],

        // Allows putting test versions of migrations under test/resources/evolutions/default
        bind[EvolutionsReader].toInstance(new ClassLoaderEvolutionsReader)
      )
      .build()

}
