package controllers.enquiries

import controllers.{BaseController, TeamSpecificActionRefiner}
import domain.Enquiry.{FormData => Data}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, RequestHeader}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}
import domain.Enquiry.{FormData => Data}
import play.api.Configuration
import play.api.libs.json.Json

@Singleton
class EnquiryController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService,
  config: Configuration
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._
  import securityService._

  private val baseForm = Form(mapping(
    "text" -> nonEmptyText
  )(Data.apply)(Data.unapply))

  private val initialTeam: Team = Teams.fromId(config.get[String]("app.enquiries.initialTeamId"))

  private def render(f: Form[Data])(implicit req: RequestHeader) =
    Ok(views.html.enquiry.form(f))

  def form(): Action[AnyContent] = SigninRequiredAction { implicit request =>
    render(baseForm)
  }

  def submit(): Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    baseForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(formWithErrors)),
      formData => {
        val enquiry = Enquiry(
          id = None,
          universityID = request.context.user.get.universityId.get,
          team = initialTeam
        )

        val message = domain.MessageSave(
          text = formData.text,
          sender = MessageSender.Client,
          teamMember = None
        )

        service.save(enquiry, message).successMap { _ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
        }

      }
    )
  }

}
