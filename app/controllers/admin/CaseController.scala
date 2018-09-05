package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.admin.CaseController._
import controllers.refiners.{CanEditCaseActionRefiner, CanViewCaseActionRefiner, CanViewTeamActionRefiner}
import domain._
import domain.dao.CaseDao.Case
import helpers.{FormHelpers, JavaTime}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.CaseService
import services.tabula.ProfileService
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object CaseController {
  case class CaseFormData(
    clients: Set[UniversityID],
    incidentDate: OffsetDateTime,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    cause: CaseCause,
    caseType: Option[CaseType],
  )

  def form(team: Team, profileService: ProfileService)(implicit t: TimingContext, executionContext: ExecutionContext): Form[CaseFormData] = {
    def isValid(u: UniversityID): Boolean =
      Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
        .toOption.exists(_.isRight)

    Form(mapping(
      "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u))).verifying("error.required", _.exists(_.string.nonEmpty)),
      "incidentDate" -> FormHelpers.offsetDateTime,
      "onCampus" -> boolean,
      "notifiedPolice" -> boolean,
      "notifiedAmbulance" -> boolean,
      "notifiedFire" -> boolean,
      "cause" -> CaseCause.formField,
      "caseType" -> optional(CaseType.formField).verifying("error.caseType.invalid", t => (CaseType.valuesFor(team).isEmpty && t.isEmpty) || t.exists(CaseType.valuesFor(team).contains))
    )(CaseFormData.apply)(CaseFormData.unapply))
  }

  case class CaseLinkFormData(
    linkType: CaseLinkType,
    targetID: UUID
  )

  def caseLinkForm(sourceID: UUID, caseService: CaseService)(implicit t: TimingContext): Form[CaseLinkFormData] = {
    def isValid(id: UUID): Boolean =
      Try(Await.result(caseService.find(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(mapping(
      "linkType" -> CaseLinkType.formField,
      "targetID" -> uuid.verifying("error.linkTarget.same", _ != sourceID).verifying("error.required", id => isValid(id))
    )(CaseLinkFormData.apply)(CaseLinkFormData.unapply))
  }
}

@Singleton
class CaseController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewCaseActionRefiner._
  import canViewTeamActionRefiner._
  import canEditCaseActionRefiner._

  def view(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    cases.findFull(caseKey).successMap { c =>
      Ok(views.html.admin.cases.view(c))
    }
  }

  def createForm(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId) { implicit teamRequest =>
    Ok(views.html.admin.cases.create(teamRequest.team, form(teamRequest.team, profiles)))
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.create(teamRequest.team, formWithErrors))
      ),
      data => {
        val c = Case(
          id = None, // Set by service
          key = None, // Set by service
          created = JavaTime.offsetDateTime,
          incidentDate = data.incidentDate,
          team = teamRequest.team,
          version = JavaTime.offsetDateTime,
          state = IssueState.Open,
          onCampus = data.onCampus,
          notifiedPolice = data.notifiedPolice,
          notifiedAmbulance = data.notifiedAmbulance,
          notifiedFire = data.notifiedFire,
          originalEnquiry = None, // TODO
          caseType = data.caseType,
          cause = data.cause
        )

        val clients = data.clients.filter(_.string.nonEmpty)

        cases.create(c, clients).successMap { created =>
          Redirect(controllers.admin.routes.CaseController.view(created.key.get))
            .flashing("success" -> Messages("flash.case.created", created.key.get.string))
        }
      }
    )
  }

  def linkForm(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.link(caseRequest.`case`, caseLinkForm(caseRequest.`case`.id.get, cases)))
  }

  def link(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseLinkForm(caseRequest.`case`.id.get, cases).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.link(caseRequest.`case`, formWithErrors))
      ),
      data =>
        cases.addLink(data.linkType, caseRequest.`case`.id.get, data.targetID).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.linked"))
        }
    )
  }

}
