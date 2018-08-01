package services

import com.google.inject.ImplementedBy
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import system.ImplicitRequestContext
import warwick.sso._

@ImplementedBy(classOf[SecurityServiceImpl])
trait SecurityService {
  type AuthActionBuilder = ActionBuilder[AuthenticatedRequest, AnyContent]

  def SigninAwareAction: AuthActionBuilder
  def RequiredRoleAction(role: RoleName): AuthActionBuilder
  def RequiredActualUserRoleAction(role: RoleName): AuthActionBuilder

  def RequireSysadmin: AuthActionBuilder
}

@Singleton
class SecurityServiceImpl @Inject()(
                                     val sso: SSOClient,
                                     parse: PlayBodyParsers
                                   ) extends SecurityService with Results with Rendering with AcceptExtractors with ImplicitRequestContext {

  private def defaultParser = parse.default

  override def SigninAwareAction: AuthActionBuilder = sso.Lenient(defaultParser)
  override def RequiredRoleAction(role: RoleName): AuthActionBuilder = sso.RequireRole(role, forbidden)(defaultParser)
  override def RequiredActualUserRoleAction(role: RoleName): AuthActionBuilder = sso.RequireActualUserRole(role, forbidden)(defaultParser)

  val RequireSysadmin: AuthActionBuilder = RequiredActualUserRoleAction(RoleName("sysadmin"))

  private def forbidden(request: AuthenticatedRequest[_]) = {
    render {
      case Accepts.Json() =>
        request.context.user
          .map(user => forbiddenResponse(user, request.path))
          .getOrElse(unauthorizedResponse)
      case _ =>
        val userName = getUserDisplayName(request.context)
        Forbidden(views.html.errors.forbidden(userName)(requestContext(request)))
    }(request)
  }

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
