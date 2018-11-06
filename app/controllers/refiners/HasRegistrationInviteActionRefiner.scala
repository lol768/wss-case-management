package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{PermissionService, RegistrationService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class HasRegistrationInviteActionRefiner @Inject()(
  registrationService: RegistrationService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitRegistrationService: RegistrationService = registrationService

  def HasRegistrationInviteAction: ActionBuilder[RegistrationSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithRegistration

}
