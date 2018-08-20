package controllers

import domain.Teams
import javax.inject.{Inject, Singleton}
import services.{EmailService, EnquiryService, RegistrationService, SecurityService}

import scala.concurrent.ExecutionContext

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  enquiries: EnquiryService,
  registrationService: RegistrationService
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def home = SigninRequiredAction.async { implicit request =>
    val client = request.context.user.get.universityId.get
    enquiries.findEnquiriesForClient(client).successFlatMap(enquiries =>
      registrationService.get(client).successMap(registration =>
        Ok(views.html.home(Teams.all, enquiries, registration))
      )
    )
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
