package controllers.refiners

import java.util.UUID

import domain.Enquiry
import domain.dao.CaseDao.Case
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.{CaseService, EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext

import scala.concurrent.ExecutionContext

@Singleton
class ClientIssueActionFilters @Inject()(
  enquiryService: EnquiryService,
  caseService: CaseService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private implicit val implicitEnquiryService: EnquiryService = enquiryService
  private implicit val implicitCaseService: CaseService = caseService

  private val CanClientViewIssue = PermissionsFilter[IssueSpecificRequest] { implicit request =>
    request.issue match {
      case enquiry: Enquiry =>
        permissionService.canClientViewEnquiry(request.context.user.get, enquiry.id.get)
      case clientCase: Case =>
        permissionService.canClientViewCase(request.context.user.get, clientCase.id.get)
    }
  }

  def CanClientViewIssueAction(id: UUID): ActionBuilder[IssueSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithIssue(id) andThen CanClientViewIssue

  private val CanAddClientMessage = PermissionsFilter[IssueSpecificRequest] { implicit request =>
    request.issue match {
      case enquiry: Enquiry =>
        permissionService.canAddClientMessageToEnquiry(request.context.user.get, enquiry.id.get)
      case clientCase: Case =>
        permissionService.canAddClientMessageToCase(request.context.user.get, clientCase.id.get)
    }
  }

  def CanAddClientMessageToIssueAction(id: UUID): ActionBuilder[IssueSpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithIssue(id) andThen CanAddClientMessage

}
