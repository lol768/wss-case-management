package controllers

import domain.Teams
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import services._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  enquiries: EnquiryService,
  registrationService: RegistrationService,
  audit: AuditService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val client = request.context.user.get.universityId.get

    ServiceResults.zip(
      enquiries.findEnquiriesForClient(client),
      registrationService.get(client)
    ).successFlatMap { case (e, registration) =>
      val view: Future[ServiceResult[Result]] =
        Future.successful(Right(Ok(views.html.home(Teams.all, e, registration))))

      // Record an EnquiryView event for the first enquiry as that's open by default in the accordion
      val recordAudit =
        e.headOption.map { e =>
          audit.audit('EnquiryView, e.enquiry.id.get.toString, 'Enquiry, Json.obj())(view)
        }.getOrElse(view)

      recordAudit.successMap(identity)
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
