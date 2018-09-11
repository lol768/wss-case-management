package controllers

import domain.Teams
import helpers.ServiceResults
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, EnquiryService, RegistrationService, SecurityService}

import scala.concurrent.ExecutionContext

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  enquiries: EnquiryService,
  registrationService: RegistrationService,
  cases: CaseService
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val client = request.context.user.get.universityId.get

    ServiceResults.zip(
      enquiries.findEnquiriesForClient(client),
      registrationService.get(client)
    ).successMap { case (e, registration) =>
      Ok(views.html.home(Teams.all, e, registration))
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
