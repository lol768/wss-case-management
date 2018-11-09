package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class CanAddTeamMessageToEnquiryActionRefiner @Inject()(
  enquiryService: EnquiryService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitEnquiryService: EnquiryService = enquiryService

  private val CanAddTeamMessage = PermissionsFilter[EnquirySpecificRequest] { implicit request =>
    permissionService.canAddTeamMessageToEnquiry(request.context.user.get, request.enquiry.id)
  }

  def CanAddTeamMessageToEnquiryAction(enquiryKey: IssueKey): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithEnquiry(enquiryKey) andThen CanAddTeamMessage

}
