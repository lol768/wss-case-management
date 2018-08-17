package controllers.registration

import controllers.BaseController
import domain._
import helpers.FormHelpers
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Result}
import services.{NotificationService, RegistrationService, SecurityService}
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(
  securityService: SecurityService,
  registrationService: RegistrationService,
  notificationService: NotificationService
)(implicit executionContext: ExecutionContext) extends BaseController with I18nSupport {

  import securityService._

  private val registerForm = Form(mapping(
    "gp" -> nonEmptyText,
    "tutor" -> nonEmptyText,
    "disabilities" -> set(of[Disability](Disabilities.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list")),
    "medications" -> set(of[Medication](Medications.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list")),
    "appointment-adjustments" -> text,
    "referrals" -> set(of[RegistrationReferral](RegistrationReferrals.Formatter))
      .verifying(FormHelpers.nonEmpty("error.required.list"))
  )(RegistrationData.apply)(RegistrationData.unapply))

  def form: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    withOptionalRegistration {
      case Some(registration) =>
        Future.successful(Ok(views.html.registration(registerForm.fill(registration.data), Some(registration))))
      case _ =>
        Future.successful(Ok(views.html.registration(registerForm, None)))
    }
  }

  def submit: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val universityID = request.context.user.get.universityId.get
    registerForm.bindFromRequest.fold(
      formWithErrors => {
        withOptionalRegistration { option =>
          Future.successful(Ok(views.html.registration(formWithErrors, option)))
        }
      },
      data => {
        withOptionalRegistration {
          case Some(existing) =>
            registrationService.update(universityID, data, existing.updatedDate).successMap { _ =>
              Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.registration.updated"))
            }
          case _ =>
            registrationService.save(universityID, data).successFlatMap { _ =>
              notificationService.newRegistration(universityID).successMap { _ =>
                Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.registration.complete"))
              }
            }
        }
      }
    )
  }

  private def withOptionalRegistration(f: Option[Registration] => Future[Result])(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] =
    registrationService.get(request.context.user.get.universityId.get).successFlatMap(f)

}
