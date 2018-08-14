package controllers.registration

import java.time.ZonedDateTime

import controllers.{BaseController, TeamSpecificActionRefiner, TeamSpecificRequest}
import domain._
import helpers.{FormHelpers, JavaTime}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent}
import services.RegistrationService

import scala.concurrent.{ExecutionContext, Future}

object RegistrationController {
  object Counselling {
    val form = Form(mapping(
      "counselling-types" -> set(of[CounsellingType](CounsellingTypes.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "gp" -> nonEmptyText,
      "tutor" -> nonEmptyText,
      "disabilities" -> set(of[Disability](Disabilities.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "medications" -> set(of[Medication](Medications.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "previous-counsellings" -> set(of[PreviousCounselling](PreviousCounsellings.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "appointment-availability" -> of[AppointmentAvailability](AppointmentAvailabilities.Formatter),
      "appointment-adjustments" -> text,
      "referrals" -> set(of[RegistrationReferral](RegistrationReferrals.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list"))
    )(Registrations.CounsellingData.apply)(Registrations.CounsellingData.unapply))
  }

  object StudentSupport {
    val form = Form(mapping(
      "summary" -> nonEmptyText,
      "gp" -> nonEmptyText,
      "tutor" -> nonEmptyText,
      "disabilities" -> set(of[Disability](Disabilities.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "medications" -> set(of[Medication](Medications.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list")),
      "appointment-adjustments" -> text,
      "referrals" -> set(of[RegistrationReferral](RegistrationReferrals.Formatter))
        .verifying(FormHelpers.nonEmpty("error.required.list"))
    )(Registrations.StudentSupportData.apply)(Registrations.StudentSupportData.unapply))
  }
}

@Singleton
class RegisterController @Inject()(
  registrationService: RegistrationService,
  teamSpecificActionRefiner: TeamSpecificActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController with I18nSupport {

  import teamSpecificActionRefiner._

  def form(teamId: String): Action[AnyContent] = TeamSpecificSignInRequiredAction(teamId).async { implicit request =>
    request.team.id match {
      case Teams.Counselling.id => counsellingForm
      case Teams.StudentSupport.id => studentSupportForm
      case _ => Future.successful(NotFound(views.html.errors.notFound()))
    }
  }

  def submit(teamId: String): Action[AnyContent] = TeamSpecificSignInRequiredAction(teamId).async { implicit request =>
    request.team.id match {
      case Teams.Counselling.id => counsellingSubmit
      case Teams.StudentSupport.id => studentSupportSubmit
      case _ => Future.successful(NotFound(views.html.errors.notFound()))
    }
  }

  private def counsellingForm(implicit request: TeamSpecificRequest[AnyContent]) = {
    registrationService.getCounselling(request.context.user.get.universityId.get).map(
      _.map(registration =>
        Ok(views.html.registration.counselling(RegistrationController.Counselling.form.fill(registration.data)))
      ).getOrElse(
        Ok(views.html.registration.counselling(RegistrationController.Counselling.form))
      )
    )
  }

  private def counsellingSubmit(implicit request: TeamSpecificRequest[AnyContent]) = {
    RegistrationController.Counselling.form.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(Ok(views.html.registration.counselling(formWithErrors)))
      },
      data => {
        registrationService.save(Registrations.Counselling(request.context.user.get.universityId.get, ZonedDateTime.now, data)).map(_ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> "Counselling registration complete")
        )
      }
    )
  }

  private def studentSupportForm(implicit request: TeamSpecificRequest[AnyContent]) = {
    registrationService.getStudentSupport(request.context.user.get.universityId.get).map(
      _.map(registration =>
        Ok(views.html.registration.studentsupport(RegistrationController.StudentSupport.form.fill(registration.data)))
      ).getOrElse(
        Ok(views.html.registration.studentsupport(RegistrationController.StudentSupport.form))
      )
    )
  }

  private def studentSupportSubmit(implicit request: TeamSpecificRequest[AnyContent]) = {
    RegistrationController.StudentSupport.form.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(Ok(views.html.registration.studentsupport(formWithErrors)))
      },
      data => {
        registrationService.save(Registrations.StudentSupport(request.context.user.get.universityId.get, JavaTime.zonedDateTime, data)).map(_ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> "Student Support registration complete")
        )
      }
    )
  }

}
