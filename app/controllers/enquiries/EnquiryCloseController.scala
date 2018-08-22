package controllers.enquiries

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import domain.{Enquiry, EnquiryState}
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{EnquiryService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryCloseController @Inject()(
  enquirySpecificActionRefiner: EnquirySpecificActionRefiner,
  securityService: SecurityService,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import enquirySpecificActionRefiner._

  case class CloseForm (version: OffsetDateTime)

  def closeForm(enquiry: Enquiry) = Form(mapping(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
  )(CloseForm.apply)(CloseForm.unapply))

  def close(id: UUID): Action[AnyContent] = EnquirySpecificMessagesAction(id).async { implicit request =>
    closeForm(request.enquiry).bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          Redirect(routes.EnquiryMessagesController.messages(request.enquiry.id.get))
            .flashing("error" -> formWithErrors.errors.map(_.format).mkString(", "))
        )
      },
      formData => {
        service.updateState(request.enquiry, EnquiryState.Closed).successMap { e =>
          Redirect(routes.EnquiryMessagesController.messages(request.enquiry.id.get))
            .flashing("success" -> Messages("flash.enquiry.closed"))
        }
      }
    )
  }

}
