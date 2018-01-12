package helpers

import org.scalatest.{Suite, TestSuite}
import play.api.Application
import warwick.sso.{Usercode, Users}

import scala.reflect.ClassTag

trait OneAppPerSuite extends Suite with org.scalatestplus.play.guice.GuiceOneAppPerSuite {
  self: TestSuite =>

  val currentUser = Users.create(Usercode("user"), staff = true)
  implicit override lazy val app: Application = TestApplications.full(currentUser = Some(currentUser))

  def get[T : ClassTag]: T = app.injector.instanceOf[T]

}
