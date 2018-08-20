package controllers

import domain.Teams
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.mailer.Email
import services.{EmailService, EnquiryService, RegistrationService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  enquiries: EnquiryService,
  registrationService: RegistrationService,
  emailService: EmailService
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def home = SigninRequiredAction.async { implicit request =>
    val client = request.context.user.get.universityId.get
    enquiries.findEnquiriesForClient(client).successFlatMap(enquiries =>
      registrationService.get(client).successFlatMap { registration =>
        val url = s"https://augustus.warwick.ac.uk${controllers.admin.routes.ClientController.client(request.context.user.get.universityId.get).url}"
        emailService.queue(Email(
          subject = "Case Management: New registration received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.newregistration(url).toString)
        ), Seq(request.context.user.get)).successMap(_ =>
          Ok(views.html.home(Teams.all, enquiries, registration))
        )
      }
    )
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
