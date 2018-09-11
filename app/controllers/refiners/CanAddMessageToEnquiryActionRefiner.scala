package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanAddMessageToEnquiryActionRefiner @Inject()(
  config: Configuration,
  enquiryService: EnquiryService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitEnquiryService: EnquiryService = enquiryService

  private def CanAddMessage[A] = PermissionsFilter[EnquirySpecificRequest] { implicit request =>
    permissionService.canAddMessageToEnquiry(request.context.user.get, request.enquiry.id.get)
  }

  def CanAddMessageToEnquiryAction(enquiryKey: IssueKey): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithEnquiry(enquiryKey) andThen CanAddMessage

}
