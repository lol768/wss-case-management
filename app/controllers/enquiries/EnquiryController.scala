package controllers.enquiries

import java.time.ZonedDateTime

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
import play.api.libs.json.Json

@Singleton
class EnquiryController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  val baseForm = Form(mapping(
    "text" -> nonEmptyText
  )(Data.apply)(Data.unapply))

  import teamSpecificActionRefiner._
  import securityService._

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

        val message = domain.MessageSave(
          text = formData.text
        )

        service.save(enquiry, message).map { result =>
          result.fold(
            showErrors,
            e => Redirect(controllers.routes.IndexController.home())
                  .flashing("success" -> Messages("flash.enquiry.received"))
          )
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
              "updatedDate" -> enquiry.version.toOffsetDateTime,
              "messages" -> messages.map { message =>
                Json.obj(
                  "from" -> message.sender,
                  "text" -> message.text,
                  "date" -> message.created.toOffsetDateTime
                )
              }
            )
          }
        ))
      )
    )
  }

}
