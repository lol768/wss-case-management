package helpers

import warwick.sso.{LoginContext, RoleName, User}

class MockLoginContext(u: Option[User], au: Option[User]) extends LoginContext {
  def this(u: Option[User]) = this(u, u)

  override val user: Option[User] = u
  override val actualUser: Option[User] = au

  override def loginUrl(target: Option[String]): String = "https://example.com/login"

  override def userHasRole(role: RoleName) = hasRole(u, role)
  override def actualUserHasRole(role: RoleName) = hasRole(au, role)

  private def hasRole(forUser: Option[User], role: RoleName) = forUser.nonEmpty
}
