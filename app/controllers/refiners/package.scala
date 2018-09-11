package controllers

import domain.{IssueKey, Teams}
import play.api.mvc.{ActionRefiner, RequestHeader, Result, Results}
import services.{CaseService, EnquiryService}
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

package object refiners {

  def WithEnquiry(enquiryKey: IssueKey)(implicit enquiryService: EnquiryService, requestContextBuilder: RequestHeader => RequestContext, ec: ExecutionContext): ActionRefiner[AuthenticatedRequest, EnquirySpecificRequest] =
    new ActionRefiner[AuthenticatedRequest, EnquirySpecificRequest] {
      override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, EnquirySpecificRequest[A]]] = {
        implicit val requestContext: RequestContext = requestContextBuilder(request)

        enquiryService.get(enquiryKey).map {
          case Right(enquiry) =>
            Right(new EnquirySpecificRequest[A](enquiry, request))

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

}
