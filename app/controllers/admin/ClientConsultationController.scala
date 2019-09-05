package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.admin.ClientConsultationController._
import controllers.refiners.CanViewCaseActionRefiner
import domain.{ClientConsultation, ClientConsultationSave, IssueKey}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, ClientConsultationService}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

object ClientConsultationController {
  def form(existingVersion: Option[OffsetDateTime] = None): Form[ClientConsultationSave] = Form(mapping(
    "reason" -> nonEmptyText, // Only the first question is mandatory
    "suggestedResolution" -> text,
    "alreadyTried" -> text,
    "sessionFeedback" -> text,
    "administratorOutcomes" -> text,
    "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
  )(ClientConsultationSave.apply)(ClientConsultationSave.unapply))

  def createOrUpdateForm(initialConsultation: Option[ClientConsultation]): Form[ClientConsultationSave] =
    initialConsultation match {
      case Some(existing) =>
        form(Some(existing.updatedDate))
          .fill(ClientConsultationSave(
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
  clientConsultations: ClientConsultationService,
  cases: CaseService,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewCaseActionRefiner._

  def createFormForCase(caseKey: IssueKey, universityID: UniversityID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    cases.getClients(caseRequest.`case`.id).successMap { clients =>
      val client = clients.find(_.universityID == universityID)

      if (client.isEmpty) {
        NotFound(views.html.errors.notFound())
      } else {
        Ok(views.html.admin.client.createConsultationForm(caseRequest.`case`, client.get, createOrUpdateForm(None)))
      }
    }
  }

  def createForCase(caseKey: IssueKey, universityID: UniversityID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    cases.getClients(caseRequest.`case`.id).successFlatMap { clients =>
      val client = clients.find(_.universityID == universityID)

      if (client.isEmpty) {
        Future.successful(NotFound(views.html.errors.notFound()))
      } else {
        createOrUpdateForm(None).bindFromRequest().fold(
          formWithErrors => Future.successful(Ok(views.html.admin.client.createConsultationForm(caseRequest.`case`, client.get, formWithErrors))),
          data => clientConsultations.save(universityID, data, currentUser().usercode).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.consultation.updated"))
          }
        )
      }
    }
  }

  def editFormForCase(caseKey: IssueKey, universityID: UniversityID, id: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    ServiceResults.zip(
      cases.getClients(caseRequest.`case`.id),
      clientConsultations.get(universityID)
    ).successMap { case (clients, consultations) =>
      val client = clients.find(_.universityID == universityID)
      val consultation = consultations.find(_.id == id)

      if (client.isEmpty || consultation.isEmpty) {
        NotFound(views.html.errors.notFound())
      } else {
        Ok(views.html.admin.client.editConsultationForm(consultation.get, caseRequest.`case`, client.get, createOrUpdateForm(consultation)))
      }
    }
  }

  def editForCase(caseKey: IssueKey, universityID: UniversityID, id: UUID): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    // Verify that the University ID is one of the clients on the case
    ServiceResults.zip(
      cases.getClients(caseRequest.`case`.id),
      clientConsultations.get(universityID)
    ).successFlatMap { case (clients, consultations) =>
      val client = clients.find(_.universityID == universityID)
      val consultation = consultations.find(_.id == id)

      if (client.isEmpty || consultation.isEmpty) {
        Future.successful(NotFound(views.html.errors.notFound()))
      } else {
        createOrUpdateForm(consultation).bindFromRequest().fold(
          formWithErrors => Future.successful(Ok(views.html.admin.client.editConsultationForm(consultation.get, caseRequest.`case`, client.get, formWithErrors))),
          data => clientConsultations.update(universityID, id, data, currentUser().usercode, data.version.get).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.consultation.updated"))
          }
        )
      }
    }
  }

}
