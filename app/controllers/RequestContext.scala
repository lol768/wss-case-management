package controllers

import play.api.mvc.{Flash, RequestHeader}
import services.Navigation
import warwick.sso.{AuthenticatedRequest, SSOClient, User}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

case class RequestContext(
  path: String,
  user: Option[User],
  actualUser: Option[User],
  loginUrl: String,
  logoutUrl: String,
  navigation: Seq[Navigation],
  flash: Flash,
  userAgent: Option[String],
  ipAddress: String
) {
  def isMasquerading: Boolean = user != actualUser
}

object RequestContext {

  def authenticated(sso: SSOClient, request: AuthenticatedRequest[_], navigation: Seq[Navigation]): RequestContext = RequestContext(sso, request, request.context.user, request.context.actualUser, navigation)

  def authenticated(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation]): RequestContext = {
    val eventualRequestContext = sso.withUser(request) { loginContext =>
      Future.successful(Right(RequestContext(sso, request, loginContext.user, loginContext.actualUser, navigation)))
    }.map(_.right.get)

    Await.result(eventualRequestContext, Duration.Inf)
  }

  def anonymous(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation]): RequestContext = RequestContext(sso, request, None, None, navigation)

  def apply(sso: SSOClient, request: RequestHeader, user: Option[User], actualUser: Option[User], navigation: Seq[Navigation]): RequestContext = {
    val target = (if (request.secure) "https://" else "http://") + request.host + request.path
    val linkGenerator = sso.linkGenerator(request)
    linkGenerator.setTarget(target)

    RequestContext(
      path = request.path,
      user = user,
      actualUser = actualUser,
      loginUrl = linkGenerator.getLoginUrl,
      logoutUrl = linkGenerator.getLogoutUrl,
      navigation = navigation,
      flash = Try(request.flash).toOption.getOrElse(Flash()),
      userAgent = request.headers.get("User-Agent"),
      ipAddress = request.remoteAddress
    )
  }

}
