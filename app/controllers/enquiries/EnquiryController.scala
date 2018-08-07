package controllers.enquiries

import java.time.ZonedDateTime

import controllers.{BaseController, RequestContext, TeamSpecificActionRefiner}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, RequestHeader}
import play.api.data.Form
import play.api.data.Forms._
import services.{EnquiryService, RegistrationService}

import scala.concurrent.{ExecutionContext, Future}
import domain.Enquiry.{FormData => Data}

@Singleton
class EnquiryController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController with I18nSupport {

  val baseForm = Form(mapping(
    "text" -> nonEmptyText(maxLength = 2000)
  )(Data.apply)(Data.unapply))

  import teamSpecificActionRefiner._

  private def render(team: Team, f: Form[Data])(implicit req: RequestHeader, ctx: RequestContext) =
    Ok(views.html.enquiry.form(team, f))

  def form(teamId: String): Action[AnyContent] = TeamSpecificSignInRequiredAction(teamId) { implicit request =>
    render(request.team, baseForm)
  }

  def submit(teamId: String): Action[AnyContent] = TeamSpecificSignInRequiredAction(teamId).async { implicit request =>
    baseForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(request.team, formWithErrors)),
      formData => {
        val enquiry = Enquiry(
          id = None,
          universityID = request.context.user.get.universityId.get,
          team = request.team
        )

        val message = MessageData(
          formData.text,
          MessageSender.Client,
          ZonedDateTime.now()
        )

        service.save(enquiry, message).map { enquiry =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> "Your enquiry has been received")
        }
      }
    )
  }

}
