package controllers

import domain.{IssueKey, Teams}
import helpers.ServiceResults.ServiceResult
import play.api.mvc._
import services.{CaseService, EnquiryService}
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

        enquiryService.get(enquiryKey).map {
          case Right((enquiry, messages)) =>
            Right(new EnquirySpecificRequest[A](enquiry, messages, request))

          case _ =>
            Left(Results.NotFound(views.html.errors.notFound()))
        }
      }

      override protected def executionContext: ExecutionContext = ec
    }

  def WithCase(caseKey: IssueKey)(implicit caseService: CaseService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, CaseSpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, CaseSpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        caseService.find(caseKey).map {
          case Right(c) =>
            Right(new CaseSpecificRequest[A](c, request))
          case _ =>
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
