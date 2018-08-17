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

@Singleton
class EnquiryController @Inject()(
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  val baseForm = Form(mapping(
    "text" -> nonEmptyText
  )(Data.apply)(Data.unapply))

  import securityService._
  import teamSpecificActionRefiner._

  private def render(team: Team, f: Form[Data])(implicit req: RequestHeader) =
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
