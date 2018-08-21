package controllers.admin

import controllers.{BaseController, TeamSpecificActionRefiner}
import domain._
import helpers.ServiceResults._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.tabula.ProfileService
import services.{ClientSummaryService, NotificationService, RegistrationService}
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientController @Inject()(
  profileService: ProfileService,
  registrationService: RegistrationService,
  clientSummaryService: ClientSummaryService,
  notificationService: NotificationService,
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  val form = Form(mapping(
    "notes" -> text,
    "alternative-contact-number" -> text,
    "alternative-email-address" -> text,
    "risk-status" -> optional(ClientRiskStatus.formField),
    "reasonable-adjustments" -> set(ReasonableAdjustment.formField),
    "alert-flags" -> set(AlertFlag.formField)
  )(ClientSummaryData.apply)(ClientSummaryData.unapply))

  private def clientInformation(universityID: UniversityID): Future[ServiceResult[(SitsProfile, Option[Registration], Option[ClientSummary])]] = {
    val profile = profileService.getProfile(universityID).map(_.value)
    val registration = registrationService.get(universityID)
    val clientSummary = clientSummaryService.get(universityID)

    zip(profile, registration, clientSummary)
  }

  def client(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    clientInformation(universityID).successMap { case (profile, registration, clientSummary) =>
      val f = clientSummary match {
        case Some(cs) => form.fill(cs.data)
        case _ => form
      }

      Ok(views.html.admin.client.client(profile, registration, clientSummary, f))
    }
  }

  def updateSummary(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    clientInformation(universityID).successFlatMap { case (profile, registration, clientSummary) =>
      form.bindFromRequest.fold(
        formWithErrors => Future.successful(Ok(views.html.admin.client.client(profile, registration, clientSummary, formWithErrors))),
        data => {
          val f =
            if (clientSummary.isEmpty) clientSummaryService.save(universityID, data)
            else clientSummaryService.update(universityID, data, clientSummary.get.updatedDate)

          f.map(_.fold(
            showErrors,
            summary => Ok(views.html.admin.client.client(profile, registration, Some(summary), form, Some(Messages("flash.client.summary.updated"))))
          ))
        }
      )
    }
  }

  def invite(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    notificationService.registrationInvite(universityID).successMap { _ =>
      Redirect(routes.ClientController.client(universityID)).flashing("success" -> Messages("flash.client.registration.invited"))
    }
  }

}
