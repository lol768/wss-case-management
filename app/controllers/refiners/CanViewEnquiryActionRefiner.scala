package controllers.refiners

import domain.IssueKey
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CanViewEnquiryActionRefiner @Inject()(
  config: Configuration,
  enquiryService: EnquiryService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitEnquiryService: EnquiryService = enquiryService

  private def CanViewEnquiry = new ActionFilter[EnquirySpecificRequest] {
    override protected def filter[A](request: EnquirySpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      permissionService.canViewEnquiry(request.context.user.get, request.enquiry.id.get).map(_.fold(
        errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
        canViewEnquiry =>
          if (canViewEnquiry) None
          else Some(Results.NotFound(views.html.errors.notFound()))
      ))
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def CanViewEnquiryAction(enquiryKey: IssueKey): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithEnquiry(enquiryKey) andThen CanViewEnquiry

}
