package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanEditEnquiryActionRefiner @Inject()(
  enquiryService: EnquiryService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitEnquiryService: EnquiryService = enquiryService

  private val CanEditEnquiry = PermissionsFilter[EnquirySpecificRequest] { implicit request =>
    permissionService.canEditEnquiry(request.context.user.get.usercode, request.enquiry.id.get)
  }

  def CanEditEnquiryAction(enquiryKey: IssueKey): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithEnquiry(enquiryKey) andThen CanEditEnquiry

}
