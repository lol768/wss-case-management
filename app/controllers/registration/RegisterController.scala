package controllers.registration

import controllers.BaseController
import controllers.refiners.HasRegistrationInviteActionRefiner
import domain._
import helpers.FormHelpers
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent}
import services.RegistrationService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(
  registrationService: RegistrationService,
  hasRegistrationInviteActionRefiner: HasRegistrationInviteActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController with I18nSupport {

  import hasRegistrationInviteActionRefiner._

  private val registerForm = Form(mapping(
    "gp" -> nonEmptyText,
    "tutor" -> nonEmptyText,
    "disabilities" -> set(of[Disability](Disabilities.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list")),
    "medications" -> set(of[Medication](Medications.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list")),
    "appointment-adjustments" -> text,
    "referrals" -> set(of[RegistrationReferral](RegistrationReferrals.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list")),
    "consent-privacy-statement" -> optional(boolean).verifying("error.privacyStatementConsent.required", _.contains(true))
  )(RegistrationData.apply)(RegistrationData.unapply))

  def form: Action[AnyContent] = HasRegistrationInviteAction.async { implicit request =>
    request.registration.data match {
      case Some(data) =>
        Future.successful(Ok(views.html.registration(registerForm.fill(data.copy(consentPrivacyStatement = None)), Some(data))))
      case _ =>
        Future.successful(Ok(views.html.registration(registerForm, None)))
    }
  }

  def submit: Action[AnyContent] = HasRegistrationInviteAction.async { implicit request =>
    registerForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(views.html.registration(formWithErrors, request.registration.data))),
      data => {
        registrationService.register(currentUser().universityId.get, data, request.registration.updatedDate).successMap(_ =>
          Redirect(controllers.routes.IndexController.home())
            .flashing("success" -> Messages(request.registration.data.map(_ => "flash.registration.updated").getOrElse("flash.registration.complete")))
        )
      }
    )
  }

}
