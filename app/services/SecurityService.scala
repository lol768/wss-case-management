package services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import controllers.RequestContext
import helpers.Json.JsonClientError
import play.api.libs.json.Json
import play.api.mvc._
import warwick.sso._

@ImplementedBy(classOf[SecurityServiceImpl])
trait SecurityService {
  def RequireSysadmin(parser: BodyParser[AnyContent]): ActionBuilder[AuthenticatedRequest, AnyContent]
}

@Singleton
class SecurityServiceImpl @Inject()(sso: SSOClient) extends SecurityService with Results with Rendering with AcceptExtractors {

  override def RequireSysadmin(parser: BodyParser[AnyContent]): ActionBuilder[AuthenticatedRequest, AnyContent] = sso.RequireRole(RoleName("sysadmin"), forbidden)(parser)

  private def forbidden(request: AuthenticatedRequest[_]) =
    render {
      case Accepts.Json() =>
        request.context.user
          .map(user => forbiddenResponse(user, request.path))
          .getOrElse(unauthorizedResponse)
      case _ =>
        val userName = getUserDisplayName(request.context)
        val requestContext = RequestContext.authenticated(sso, request, Nil)
        Forbidden(views.html.errors.forbidden(userName)(requestContext))
    }(request)

  private def getUserDisplayName(context: LoginContext): Option[String] = {
    import context._

    for {
      name <- user.flatMap(_.name.first)
      actualName <- actualUser.flatMap(_.name.first)
    } yield {
      if (isMasquerading)
        s"$name (really $actualName)"
      else
        name
    }
  }

  private def forbiddenResponse(user: User, path: String) =
    Forbidden(Json.toJson(JsonClientError(status = "forbidden", errors = Seq(s"User ${user.usercode.string} does not have permission to access $path"))))

  private val unauthorizedResponse =
    Unauthorized(Json.toJson(JsonClientError(status = "unauthorized", errors = Seq("You are not signed in.  You may authenticate through Web Sign-On."))))
}
