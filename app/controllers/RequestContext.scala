package controllers

import play.api.mvc.RequestHeader
import services.Navigation
import warwick.sso.{AuthenticatedRequest, SSOClient, User}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class RequestContext(
  path: String,
  user: Option[User],
  loginUrl: String,
  logoutUrl: String,
  navigation: Seq[Navigation],
  userAgent: Option[String],
  ipAddress: String
)

object RequestContext {

  def authenticated(sso: SSOClient, request: AuthenticatedRequest[_], navigation: Seq[Navigation]): RequestContext = RequestContext(sso, request, request.context.user, navigation)

  def authenticated(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation]): RequestContext = {
    val eventualRequestContext = sso.withUser(request) { loginContext =>
      Future.successful(Right(RequestContext(sso, request, loginContext.user, navigation)))
    }.map(_.right.get)

    Await.result(eventualRequestContext, Duration.Inf)
  }

  def anonymous(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation]): RequestContext = RequestContext(sso, request, None, navigation)

  def apply(sso: SSOClient, request: RequestHeader, user: Option[User], navigation: Seq[Navigation]): RequestContext = {
    val target = (if (request.secure) "https://" else "http://") + request.host + request.path
    val linkGenerator = sso.linkGenerator(request)
    linkGenerator.setTarget(target)

    RequestContext(
      path = request.path,
      user = user,
      loginUrl = linkGenerator.getLoginUrl,
      logoutUrl = linkGenerator.getLogoutUrl,
      navigation = navigation,
      userAgent = request.headers.get("User-Agent"),
      ipAddress = request.remoteAddress
    )
  }

}
