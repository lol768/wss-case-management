package controllers

import java.util.UUID

import domain.{IssueKey, Teams}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import play.api.mvc._
import services.{AppointmentService, CaseService, EnquiryService, RegistrationService}
import system.Roles
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

package object refiners {

  def WithEnquiry(enquiryKey: IssueKey)(implicit enquiryService: EnquiryService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, EnquirySpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, EnquirySpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, EnquirySpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        ServiceResults.zip(
          enquiryService.get(enquiryKey),
          enquiryService.getLastUpdatedMessageDate(enquiryKey)
        ).map {
          case Right((enquiry, lastUpdatedMessageDate)) =>
            Right(new EnquirySpecificRequest[A](enquiry, lastUpdatedMessageDate, request))

          case _ =>
            Left(Results.NotFound(views.html.errors.notFound()))
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithCase(keyOrId: String)(implicit caseService: CaseService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, CaseSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        Try(caseService.find(IssueKey.apply(keyOrId)))
          .toOption
          .getOrElse(caseService.find(UUID.fromString(keyOrId)))
          .map {
            case Right(c) =>
              Right(new CaseSpecificRequest[A](c, request))
            case _ =>
              Left(Results.NotFound(views.html.errors.notFound()))
          }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithCase(caseKey: IssueKey)(implicit caseService: CaseService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] =
    WithCase(caseKey.string)

  def WithCaseNote(uuid: UUID)(implicit caseService: CaseService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, CaseNoteSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, CaseNoteSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, CaseNoteSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        caseService.getNote(uuid)
          .map {
            case Right(cn) =>
              Right(new CaseNoteSpecificRequest[A](cn, request))
            case _ =>
              Left(Results.NotFound(views.html.errors.notFound()))
          }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithIssue(id: UUID)(implicit enquiryService: EnquiryService, caseService: CaseService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, IssueSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, IssueSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, IssueSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        enquiryService.get(id).flatMap {
          case Right(enquiry) =>
            enquiryService.getLastUpdatedMessageDate(enquiry.key).map(_.fold(
              errors => throw errors.head.cause.getOrElse(new Exception(errors.head.message)),
              lastMessageDate => Right(new IssueSpecificRequest[A](enquiry, Some(lastMessageDate), request))
            ))
          case _ =>
            caseService.find(id).flatMap {
              case Right(clientCase) =>
                caseService.getLastUpdatedMessageDates(clientCase.key).map(_.fold(
                  errors =>
                    throw errors.head.cause.getOrElse(new Exception(errors.head.message)),
                  lastMessageDates =>
                    Right(new IssueSpecificRequest[A](clientCase, lastMessageDates.get(request.user.get.universityId.get), request))
                ))
              case _ =>
                Future.successful(Left(Results.NotFound(views.html.errors.notFound())))
            }
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithAppointment(appointmentKey: IssueKey)(implicit appointmentService: AppointmentService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, AppointmentSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, AppointmentSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, AppointmentSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        appointmentService.find(appointmentKey).map {
          case Right(appointment) =>
            Right(new AppointmentSpecificRequest[A](appointment, request))
          case e =>
            Left(Results.NotFound(views.html.errors.notFound()))
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithTeam(teamId: String)(implicit requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, TeamSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, TeamSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, TeamSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)
        Future.successful {
          Try(Teams.fromId(teamId)).toOption.map(t => Right(new TeamSpecificRequest[A](t, request)))
            .getOrElse(Left(Results.NotFound(views.html.errors.notFound())))
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithRegistration(implicit registrationService: RegistrationService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, RegistrationSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, RegistrationSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, RegistrationSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        registrationService.get(request.user.get.universityId.get).map {
          case Right(Some(registration)) =>
            Right(new RegistrationSpecificRequest[A](registration, request))
          case _ =>
            Left(Results.NotFound(views.html.errors.notFound()))
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def PermissionsFilter[R[A] <: AuthenticatedRequest[A]](check: R[_] => Future[ServiceResult[Boolean]])(implicit requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionFilter[R] = new ActionFilter[R] {
    protected def filter[A](request: R[A]): Future[Option[Result]] = {
      implicit val requestContext: RequestContext = requestContextBuilder(request)

      val hasPermissions: Future[ServiceResult[Boolean]] =
        if (request.context.user.isEmpty)
          Future.successful(Right(false))
        else if (request.context.user == request.context.actualUser && request.context.userHasRole(Roles.Sysadmin))
          Future.successful(Right(true))
        else
          check(request)

      hasPermissions.map(_.fold(
        errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
        canDo =>
          if (canDo) None
          else Some(Results.NotFound(views.html.errors.notFound()))
      ))
    }
    override protected def executionContext: ExecutionContext = ec
  }

}
