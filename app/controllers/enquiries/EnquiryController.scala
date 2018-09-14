package controllers.enquiries

import com.google.common.io.Files
import controllers.BaseController
import controllers.refiners.CanViewEnquiryActionRefiner
import domain.Enquiry.{FormData => Data}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData, RequestHeader}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryController @Inject()(
  enquirySpecificActionRefiner: CanViewEnquiryActionRefiner,
  securityService: SecurityService,
  service: EnquiryService,
  config: Configuration
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private val initialTeam: Team = Teams.fromId(config.get[String]("app.enquiries.initialTeamId"))

  private def render(f: Form[Data])(implicit req: RequestHeader) =
    Ok(views.html.enquiry.form(f))

  def form(): Action[AnyContent] = SigninRequiredAction { implicit request =>
    render(Enquiry.form)
  }

  def submit(): Action[MultipartFormData[TemporaryFile]] = SigninRequiredAction(parse.multipartFormData).async { implicit request =>
    Enquiry.form.bindFromRequest().fold(
      formWithErrors => Future.successful(render(formWithErrors)),
      formData => {
        val enquiry = Enquiry(
          id = None,
          universityID = request.context.user.get.universityId.get,
          subject = formData.subject,
          team = initialTeam
        )

        val message = domain.MessageSave(
          text = formData.text,
          sender = MessageSender.Client,
          teamMember = None
        )

        val file = request.body.file("file").map { file =>
          (Files.asByteSource(file.ref), UploadedFileSave(
            file.filename,
            file.ref.length(),
            file.contentType.getOrElse("application/octet-stream"),
            request.context.user.get.usercode
          ))
        }

        service.save(enquiry, message, file).successMap { _ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
        }

      }
    )
  }

}
