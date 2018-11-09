package system

import controllers.RequestContext
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.RequestHeader
import services.{AuditLogContext, NavigationService}
import warwick.sso.{AuthenticatedRequest, SSOClient}

trait ImplicitRequestContext extends LowPriorityRequestContextImplicits {

  @Inject
  private[this] var navigationService: NavigationService = _

  @Inject
  private[this] var ssoClient: SSOClient = _

  @Inject
  private[this] var csrfPageHelperFactory: CSRFPageHelperFactory = _

  @Inject
  private[this] var configuration: Configuration = _

  implicit def requestContext(implicit request: RequestHeader): RequestContext =
    request match {
      case req: AuthenticatedRequest[_] =>
        RequestContext.authenticated(ssoClient, req, navigationService.getNavigation(req.context), csrfPageHelperFactory, configuration)

      case req: WrappedAuthenticatedRequest[_] =>
        RequestContext.authenticated(ssoClient, req.authRequest, navigationService.getNavigation(req.authRequest.context), csrfPageHelperFactory, configuration)

      case _ =>
        RequestContext.authenticated(ssoClient, request, loginContext => navigationService.getNavigation(loginContext), csrfPageHelperFactory, configuration)
    }

  implicit val requestContextBuilder: RequestHeader => RequestContext =
    request => requestContext(request)

}

/**
  * Low priority implicits to avoid implicit ambiguity when a TimingContext is needed (since it could either
  * convert to RequestContext, or it could also go on to convert that to AuditLogcontext which is also a TimingContext).
  */
trait LowPriorityRequestContextImplicits {

  implicit def requestToAuditLogContext(implicit requestContext: RequestContext): AuditLogContext =
    AuditLogContext(
      usercode = requestContext.user.map(_.usercode),
      ipAddress = Some(requestContext.ipAddress),
      userAgent = requestContext.userAgent,
      timingData = requestContext.timingData
    )

}

trait WrappedAuthenticatedRequest[A] {
  def authRequest: AuthenticatedRequest[A]
}