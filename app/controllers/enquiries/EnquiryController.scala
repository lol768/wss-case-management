package controllers.enquiries

import java.time.OffsetDateTime

import controllers.{BaseController, RequestContext, TeamSpecificActionRefiner}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, RequestHeader}
import play.api.data.Form
import play.api.data.Forms._
import services.{EnquiryService, RegistrationService, SecurityService}

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
          text = formData.text
        )

        service.save(enquiry, message).successMap { _ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
        }

      }
    )
  }

  /**
    * Not sure if JSON is what we'll be doing so this is kind of temporary and mostly
    * just to test that we can query for a user's enquiries.
    */
  def myEnquiriesJson = SigninRequiredAction.async { implicit request =>
    service.findEnquiriesForClient(request.context.user.get.universityId.get).map(
      _.fold(
        showErrors,
        enquiries => Ok(Json.obj(
          "enquiries" -> enquiries.map {
            case (enquiry, messages) => Json.obj(
              "id" -> enquiry.id,
              "updatedDate" -> enquiry.version,
              "messages" -> messages.map { message =>
                Json.obj(
                  "from" -> message.sender,
                  "text" -> message.text,
                  "date" -> message.created
                )
              }
            )
          }
        ))
      )
    )
  }

}
