package helpers

import org.scalatest.{Suite, TestSuite}
import play.api.Application
import warwick.sso.{Usercode, Users}

import scala.reflect.ClassTag

trait OneAppPerSuite extends Suite with org.scalatestplus.play.guice.GuiceOneAppPerSuite {
  self: TestSuite =>

  implicit override lazy val app: Application = TestApplications.full(defaultUser = None)

  def get[T : ClassTag]: T = app.injector.instanceOf[T]

}
