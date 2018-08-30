package controllers.admin

import controllers.{BaseController, RequestContext, TeamSpecificActionRefiner}
import domain._
import helpers.ServiceResults._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.tabula.ProfileService
import services._
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientController @Inject()(
  profileService: ProfileService,
  registrationService: RegistrationService,
  clientSummaryService: ClientSummaryService,
  notificationService: NotificationService,
  permissionService: PermissionService,
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  val form = Form(mapping(
    "high-mental-health-risk" -> optional(boolean),
    "notes" -> text,
    "alternative-contact-number" -> text,
    "alternative-email-address" -> text,
    "risk-status" -> optional(ClientRiskStatus.formField),
    "reasonable-adjustments" -> set(ReasonableAdjustment.formField)
  )(ClientSummaryData.apply)(ClientSummaryData.unapply))

  private def clientInformation(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[(SitsProfile, Option[Registration], Option[ClientSummary])]] = {
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

      Ok(views.html.admin.client.client(profile, registration, clientSummary, f, inMentalHealthTeam))
    }
  }

  def updateSummary(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    clientInformation(universityID).successFlatMap { case (profile, registration, clientSummary) =>
      form.bindFromRequest.fold(
        formWithErrors => Future.successful(Ok(views.html.admin.client.client(profile, registration, clientSummary, formWithErrors, inMentalHealthTeam))),
        data => {
          val processedData = if (inMentalHealthTeam) data else data.copy(highMentalHealthRisk = clientSummary.flatMap(_.data.highMentalHealthRisk))
          val f =
            if (clientSummary.isEmpty) clientSummaryService.save(universityID, processedData)
            else clientSummaryService.update(universityID, processedData, clientSummary.get.updatedDate)

          f.successMap { _ =>
            Redirect(routes.ClientController.client(universityID))
              .flashing("success" -> Messages("flash.client.summary.updated"))
          }
        }
      )
    }
  }

  def invite(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    notificationService.registrationInvite(universityID).successMap { _ =>
      Redirect(routes.ClientController.client(universityID)).flashing("success" -> Messages("flash.client.registration.invited"))
    }
  }

  def registrationHistory(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    registrationService.getHistory(universityID).successMap { history =>
      Ok(Json.toJson(history)(RegistrationDataHistory.writer))
    }
  }

  private def inMentalHealthTeam(implicit request: AuthenticatedRequest[_]): Boolean =
    logErrors(
      permissionService.inTeam(request.context.user.get.usercode, Teams.MentalHealth),
      logger,
      false,
      _ => Some(s"Could not determine if ${request.context.user.get.usercode.string} was in ${Teams.MentalHealth.id}; returning false")
    )

}
