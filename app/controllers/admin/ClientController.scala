package controllers.admin

import java.util.UUID

import controllers.BaseController
import controllers.refiners.{AnyTeamActionRefiner, ValidUniversityIDActionFilter}
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services._
import services.tabula.ProfileService
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.helpers.ServiceResults._
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientController @Inject()(
  profileService: ProfileService,
  registrationService: RegistrationService,
  clientSummaryService: ClientSummaryService,
  permissionService: PermissionService,
  enquiryService: EnquiryService,
  caseService: CaseService,
  appointmentService: AppointmentService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  validUniversityIDActionFilter: ValidUniversityIDActionFilter,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import validUniversityIDActionFilter._

  private[this] val registrationInvitesEnabled = configuration.get[Boolean]("wellbeing.features.registrationInvites")

  val form = Form(
    mapping(
      "notes" -> text,
      "alternative-contact-number" -> text,
      "alternative-email-address" -> text,
      "risk-status" -> optional(ClientRiskStatus.formField),
      "reasonable-adjustments" -> set(ReasonableAdjustment.formField),
      "reasonable-adjustments-notes" -> text,
      "initial-consultation" -> optional(mapping(
        "reason" -> text,
        "suggestedResolution" -> text,
        "alreadyTried" -> text
      )(InitialConsultation.apply)(InitialConsultation.unapply))
    )(ClientSummarySave.apply)(ClientSummarySave.unapply)
      .verifying(Constraint { summary: ClientSummarySave =>
        if (summary.reasonableAdjustments.isEmpty || summary.reasonableAdjustmentsNotes.hasText)
          Valid
        else
          Invalid(ValidationError("error.reasonableAdjustments.notesEmpty"))
      })
  )

  private def clientInformation(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[(Option[SitsProfile], Option[Registration], Option[ClientSummary], ClientSummaryHistory, Seq[EnquiryListRender], Int, Int, Seq[AppointmentRender], Map[UUID, Set[Member]])]] = {
    zip(
      profileService.getProfile(universityID).map(_.value),
      registrationService.get(universityID),
      clientSummaryService.get(universityID),
      clientSummaryService.getHistory(universityID),
      enquiryService.listEnquiriesForClient(universityID),
      caseService.countOpenForClient(universityID),
      caseService.countClosedForClient(universityID),
      appointmentService.findForSearch(AppointmentSearchQuery(client = Some(universityID), startAfter = Some(JavaTime.localDate), states = Set(AppointmentState.Provisional, AppointmentState.Accepted, AppointmentState.Attended))),
    ).successFlatMapTo { case (profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments) =>
      enquiryService.getOwners(enquiries.map(_.enquiry.id).toSet).successMapTo(enquiryOwners =>
        (profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments, enquiryOwners)
      )
    }
  }

  def client(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(ValidUniversityIDRequired(universityID)).async { implicit request =>
    clientInformation(universityID).successMap { case (profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments, owners) =>
      val f = clientSummary match {
        case Some(cs) => form.fill(cs.toSave)
        case _ => form
      }

      Ok(views.html.admin.client.client(universityID, profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments, owners, f, inMentalHealthTeam, registrationInvitesEnabled))
    }
  }

  def updateSummary(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(ValidUniversityIDRequired(universityID)).async { implicit request =>
    clientInformation(universityID).successFlatMap { case (profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments, owners) =>
      form.bindFromRequest.fold(
        formWithErrors => Future.successful(Ok(views.html.admin.client.client(universityID, profile, registration, clientSummary, clientSummaryHistory, enquiries, openCases, closedCases, appointments, owners, formWithErrors, inMentalHealthTeam, registrationInvitesEnabled))),
        data => {
          val f =
            if (clientSummary.isEmpty) clientSummaryService.save(universityID, data)
            else clientSummaryService.update(universityID, data, clientSummary.get.updatedDate)

          f.successMap { _ =>
            Redirect(routes.ClientController.client(universityID))
              .flashing("success" -> Messages("flash.client.summary.updated"))
          }
        }
      )
    }
  }

  def invite(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(ValidUniversityIDRequired(universityID)).async { implicit request =>
    if (registrationInvitesEnabled) {
      registrationService.invite(universityID).successMap { _ =>
        Redirect(routes.ClientController.client(universityID)).flashing("success" -> Messages("flash.client.registration.invited"))
      }
    } else {
      Future.successful(Redirect(routes.ClientController.client(universityID)))
    }
  }

  def registrationHistory(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(ValidUniversityIDRequired(universityID)).async { implicit request =>
    registrationService.getHistory(universityID).successMap { history =>
      Ok(Json.toJson(history)(RegistrationDataHistory.writer))
    }
  }

  def cases(universityID: UniversityID): Action[AnyContent] = AnyTeamMemberRequiredAction.andThen(ValidUniversityIDRequired(universityID)).async { implicit request =>
    caseService.listForClient(universityID).successFlatMap(cases =>
      caseService.getOwners(cases.map(_.clientCase.id).toSet).successFlatMap(owners =>
        ServiceResults.futureSequence(
          cases.map(c => permissionService.canEditCase(currentUser().usercode, c.clientCase.id).map(_.map(r => c -> r)))
        ).successMap(canEdit =>
          Ok(views.html.admin.client.sections.cases(cases, owners, canEdit.toMap))
        )
      )
    )
  }

  private def inMentalHealthTeam(implicit request: AuthenticatedRequest[_]): Boolean =
    logErrors(
      permissionService.canViewTeam(request.context.user.get.usercode, Teams.MentalHealth),
      logger,
      false,
      _ => Some(s"Could not determine if ${request.context.user.get.usercode.string} was in ${Teams.MentalHealth.id}; returning false")
    ).right.get

}
