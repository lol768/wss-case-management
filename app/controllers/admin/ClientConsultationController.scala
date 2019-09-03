package controllers.admin

import java.time.OffsetDateTime

import controllers.BaseController
import controllers.admin.ClientConsultationController._
import controllers.refiners.CanViewCaseActionRefiner
import domain.{InitialConsultation, InitialConsultationSave, IssueKey}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, ClientSummaryService}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

object ClientConsultationController {
  def form(existingVersion: Option[OffsetDateTime] = None): Form[InitialConsultationSave] = Form(mapping(
    "reason" -> nonEmptyText, // Only the first question is mandatory
    "suggestedResolution" -> text,
    "alreadyTried" -> text,
    "sessionFeedback" -> text,
    "administratorOutcomes" -> text,
    "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
  )(InitialConsultationSave.apply)(InitialConsultationSave.unapply))

  def createOrUpdateForm(initialConsultation: Option[InitialConsultation]): Form[InitialConsultationSave] =
    initialConsultation match {
      case Some(existing) =>
        form(Some(existing.updatedDate))
          .fill(InitialConsultationSave(
            reason = existing.reason,
            suggestedResolution = existing.suggestedResolution,
            alreadyTried = existing.alreadyTried,
            sessionFeedback = existing.sessionFeedback,
            administratorOutcomes = existing.administratorOutcomes,
            version = Some(existing.updatedDate)
          ))

      case _ => form(None)
    }
}

@Singleton
class ClientConsultationController @Inject()(
  clientSummaries: ClientSummaryService,
  cases: CaseService,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewCaseActionRefiner._

  def formForCase(caseKey: IssueKey, universityID: UniversityID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    ServiceResults.zip(
      cases.getClients(caseRequest.`case`.id),
      clientSummaries.get(universityID)
    ).successMap { case (clients, clientSummary) =>
      val client = clients.find(_.universityID == universityID)

      if (client.isEmpty) {
        NotFound(views.html.errors.notFound())
      } else {
        Ok(views.html.admin.client.consultationForm(caseRequest.`case`, client.get, createOrUpdateForm(clientSummary.flatMap(_.initialConsultation))))
      }
    }
  }

  def saveForCase(caseKey: IssueKey, universityID: UniversityID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    ServiceResults.zip(
      cases.getClients(caseRequest.`case`.id),
      clientSummaries.get(universityID)
    ).successFlatMap { case (clients, clientSummary) =>
      val client = clients.find(_.universityID == universityID)

      if (client.isEmpty) {
        Future.successful(NotFound(views.html.errors.notFound()))
      } else {
        createOrUpdateForm(clientSummary.flatMap(_.initialConsultation)).bindFromRequest().fold(
          formWithErrors => Future.successful(Ok(views.html.admin.client.consultationForm(caseRequest.`case`, client.get, formWithErrors))),
          data => clientSummaries.recordInitialConsultation(universityID, data, currentUser().usercode).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.consultation.updated"))
          }
        )
      }
    }
  }

}
