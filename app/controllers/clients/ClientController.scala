package controllers.clients

import controllers.BaseController
import domain._
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{ClientSummaryService, RegistrationService, SecurityService}
import services.tabula.ProfileService
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientController @Inject()(
  securityService: SecurityService,
  profileService: ProfileService,
  registrationService: RegistrationService,
  clientSummaryService: ClientSummaryService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  val form = Form(mapping(
    "notes" -> text,
    "alternative-contact-number" -> text,
    "alternative-email-address" -> text,
    "risk-status" -> ClientRiskStatus.formField,
    "reasonable-adjustments" -> set(of[ReasonableAdjustment](ReasonableAdjustment.Formatter)),
    "alert-flags" -> set(AlertFlag.formField)
  )(ClientSummaryData.apply)(ClientSummaryData.unapply))

  private def clientInformation(universityID: UniversityID): Future[ServiceResult[(SitsProfile, Option[Registration], Option[ClientSummary])]] = {
    val profile = profileService.getProfile(universityID).map(_.value)
    val registration = registrationService.get(universityID)
    val clientSummary = clientSummaryService.get(universityID)

    Future.sequence(Seq(profile, registration, clientSummary)).map(ServiceResults.sequence).map {
      case Left(errors) => Left(errors)
      case Right(Seq(profile: SitsProfile @unchecked, registration: Option[Registration] @unchecked, clientSummary: Option[ClientSummary] @unchecked)) =>
        Right((profile, registration, clientSummary))
    }
  }

  // TODO permissions
  def client(universityID: UniversityID): Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    clientInformation(universityID).successMap { case (profile, registration, clientSummary) =>
      val f = clientSummary match {
        case Some(cs) => form.fill(cs.data)
        case _ => form
      }

      Ok(views.html.client.client(profile, registration, clientSummary, f))
    }
  }

  def updateSummary(universityID: UniversityID): Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    clientInformation(universityID).successFlatMap { case (profile, registration, clientSummary) =>
      form.bindFromRequest.fold(
        formWithErrors => Future.successful(Ok(views.html.client.client(profile, registration, clientSummary, formWithErrors))),
        data => {
          val f =
            if (clientSummary.isEmpty) clientSummaryService.save(universityID, data)
            else clientSummaryService.update(universityID, data, clientSummary.get.updatedDate)

          f.map(_.fold(
            showErrors,
            summary => Ok(views.html.client.client(profile, registration, Some(summary), form))
          ))
        }
      )
    }
  }

}
