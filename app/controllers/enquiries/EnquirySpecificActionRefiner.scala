package controllers.enquiries

import java.util.UUID

import domain.{Enquiry, MessageData}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc._
import services.{EnquiryService, PermissionService, SecurityService}
import system.ImplicitRequestContext
import system.Roles._
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquirySpecificActionRefiner @Inject()(
  config: Configuration,
  enquiryService: EnquiryService,
  securityService: SecurityService,
  permissionService: PermissionService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  private def WithEnquiry(enquiryId: UUID) = new ActionRefiner[AuthenticatedRequest, EnquirySpecificRequest] {
    override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, EnquirySpecificRequest[A]]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      enquiryService.get(enquiryId).map {
        case Right((enquiry, messages)) =>
          Right(new EnquirySpecificRequest[A](enquiry, messages, request))

        case _ =>
          Left(Results.NotFound(views.html.errors.notFound()))
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def HasMessagesPermissions = new ActionFilter[EnquirySpecificRequest] {
    override protected def filter[A](request: EnquirySpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      // Either I am in the Team that the Enquiry is assigned to, or I am the person who submitted it
      // or I am a sysadmin
      val hasPermissions: ServiceResult[Boolean] =
        if (request.context.user.isEmpty) Right(false)
        else if (request.context.userHasRole(Sysadmin))
          Right(true)
        else if (request.context.user.flatMap(_.universityId).contains(request.enquiry.universityID))
          Right(true)
        else
          permissionService.inTeam(request.context.user.get.usercode, request.enquiry.team)

      Future.successful {
        hasPermissions.fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          b =>
            if (b) None
            else Some(Results.Forbidden(views.html.errors.forbidden(request.context.user.flatMap(_.name.first))))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  private def TeamMember = new ActionFilter[EnquirySpecificRequest] {
    override protected def filter[A](request: EnquirySpecificRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      Future.successful {
        permissionService.inTeam(request.context.user.get.usercode, request.enquiry.team).fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          inTeam =>
            if (inTeam) None
            else Some(Results.NotFound(views.html.errors.notFound()))
        )
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def EnquirySpecificSignInRequiredAction(enquiryId: UUID): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    securityService.SigninRequiredAction andThen WithEnquiry(enquiryId)

  def EnquirySpecificMessagesAction(enquiryId: UUID): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    EnquirySpecificSignInRequiredAction(enquiryId) andThen HasMessagesPermissions

  def EnquirySpecificTeamMemberAction(enquiryId: UUID): ActionBuilder[EnquirySpecificRequest, AnyContent] =
    EnquirySpecificSignInRequiredAction(enquiryId) andThen TeamMember

}

class EnquirySpecificRequest[A](val enquiry: Enquiry, val messages: Seq[MessageData], request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request)
