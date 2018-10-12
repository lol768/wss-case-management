package helpers

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.scalatest.{BeforeAndAfterAll, Suite, TestSuite}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.reflect.ClassTag

trait OneServerPerSuite extends Suite
  with org.scalatestplus.play.guice.GuiceOneServerPerSuite
  with BeforeAndAfterAll {
  self: TestSuite =>

  val postgres = EmbeddedPostgres.builder()
    .setCleanDataDirectory(true)
    .start()

  override protected def afterAll(): Unit = {
    postgres.close()
  }

  def fakeApplicationBuilder: GuiceApplicationBuilder =
    TestApplications.fullBuilder()
      .configure(
        // Port varies so pass url in dynamically
        "slick.dbs.default.db.url" -> postgres.getJdbcUrl("postgres","postgres")
      )

  implicit override def fakeApplication: Application = fakeApplicationBuilder.build()

  def get[T : ClassTag]: T = app.injector.instanceOf[T]

}
