package controllers

import play.api.Configuration
import play.api.mvc.{Flash, RequestHeader}
import services.Navigation
import system.{CSRFPageHelper, CSRFPageHelperFactory}
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
  myWarwickBaseUrl: String,
  navigation: Seq[Navigation],
  flash: Flash,
  csrfHelper: CSRFPageHelper,
  userAgent: Option[String],
  ipAddress: String
) {
  def isMasquerading: Boolean = user != actualUser
}

object RequestContext {

  def authenticated(sso: SSOClient, request: AuthenticatedRequest[_], navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration): RequestContext =
    RequestContext(sso, request, request.context.user, request.context.actualUser, navigation, csrfHelperFactory, configuration)

  def authenticated(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration): RequestContext = {
    val eventualRequestContext = sso.withUser(request) { loginContext =>
      Future.successful(Right(RequestContext(sso, request, loginContext.user, loginContext.actualUser, navigation, csrfHelperFactory, configuration)))
    }.map(_.right.get)

    Await.result(eventualRequestContext, Duration.Inf)
  }

  def anonymous(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration): RequestContext =
    RequestContext(sso, request, None, None, navigation, csrfHelperFactory, configuration)

  def apply(sso: SSOClient, request: RequestHeader, user: Option[User], actualUser: Option[User], navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration): RequestContext = {
    val target = (if (request.secure) "https://" else "http://") + request.host + request.path
    val linkGenerator = sso.linkGenerator(request)
    linkGenerator.setTarget(target)

    RequestContext(
      path = request.path,
      user = user,
      actualUser = actualUser,
      loginUrl = linkGenerator.getLoginUrl,
      logoutUrl = linkGenerator.getLogoutUrl,
      myWarwickBaseUrl = configuration.get[String]("mywarwick.instances.0.baseUrl"),
      navigation = navigation,
      flash = Try(request.flash).getOrElse(Flash()),
      csrfHelper = transformCsrfHelper(csrfHelperFactory, request),
      userAgent = request.headers.get("User-Agent"),
      ipAddress = request.remoteAddress
    )
  }

  private[this] def transformCsrfHelper(helperFactory: CSRFPageHelperFactory, req: RequestHeader): CSRFPageHelper = {
    val token = play.filters.csrf.CSRF.getToken(req)

    val helper = helperFactory.getInstance(token)
    helper
  }

}
