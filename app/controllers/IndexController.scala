package controllers

import domain.Teams
import javax.inject.{Inject, Singleton}
import services.{EnquiryService, SecurityService}

import scala.concurrent.ExecutionContext

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  enquiries: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def home = SigninRequiredAction.async { implicit request =>
    val client = request.context.user.get.universityId.get
    enquiries.findEnquiriesForClient(client).map { result =>
      result.fold(
        showErrors,
        enquiries => Ok(views.html.home(Teams.all, enquiries))
      )
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
