package helpers

import play.api.mvc.Request
import play.api.test.FakeRequest
import warwick.sso.{MockSSOClient, User}

object FakeRequestMethods {
  implicit class RichFakeRequest[A](val req: FakeRequest[A]) extends AnyVal {
    // Sets a request attr that MockSSOClient understands
    def withUser(user: User): Request[A] =
      req.addAttr(MockSSOClient.LoginContextAttr, new MockLoginContext(Some(user)))
  }
}
