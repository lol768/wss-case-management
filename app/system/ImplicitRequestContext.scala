package system

import controllers.RequestContext
import javax.inject.Inject
import play.api.mvc.Request
import services.{AuditLogContext, NavigationService}
import warwick.sso.{AuthenticatedRequest, SSOClient}

trait ImplicitRequestContext {

  @Inject
  private[this] var navigationService: NavigationService = null

  @Inject
  private[this] var ssoClient: SSOClient = null

  implicit def requestContext(implicit request: Request[_]): RequestContext = request match {
    case req: AuthenticatedRequest[_] =>
      RequestContext.authenticated(ssoClient, req, navigationService.getNavigation(req.context))

    case req: WrappedAuthenticatedRequest[_] =>
      RequestContext.authenticated(ssoClient, req, navigationService.getNavigation(req.authRequest.context))

    case _ => RequestContext.anonymous(ssoClient, request, Nil)
  }

  implicit def requestToAuditLogContext(implicit requestContext: RequestContext): AuditLogContext =
    AuditLogContext(
      usercode = requestContext.user.map(_.usercode),
      ipAddress = Some(requestContext.ipAddress),
      userAgent = requestContext.userAgent
    )

}

trait WrappedAuthenticatedRequest[A] {
  def authRequest: AuthenticatedRequest[A]
}