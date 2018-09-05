package controllers.admin

import java.time.OffsetDateTime

import controllers.admin.CaseController._
import controllers.{BaseController, TeamSpecificActionRefiner}
import domain._
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.CaseService
import services.tabula.ProfileService
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

object CaseController {
  case class CaseFormData(
    clients: Set[UniversityID],
    incidentDate: OffsetDateTime,
    onCampus: Boolean,
    cause: CaseCause,
    caseType: Option[CaseType],
  )

  def form(team: Team, profileService: ProfileService) = Form(mapping(
    "clients" -> set(nonEmptyText.transform[UniversityID](UniversityID.apply, _.string)),
    "incidentDate" -> localDateTime.transform[OffsetDateTime](_.atZone(JavaTime.timeZone).toOffsetDateTime, _.toLocalDateTime),
    "onCampus" -> boolean,
    "cause" -> CaseCause.formField,
    "caseType" -> optional(CaseType.formField).verifying("error.caseType.invalid", t => (CaseType.valuesFor(team).isEmpty && t.isEmpty) || t.exists(CaseType.valuesFor(team).contains))
  )(CaseFormData.apply)(CaseFormData.unapply))
}

@Singleton
class CaseController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  teamSpecificActionRefiner: TeamSpecificActionRefiner,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import teamSpecificActionRefiner._

  def createForm(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId) { implicit teamRequest =>
    Ok(views.html.admin.cases.create(teamRequest.team, form(teamRequest.team, profiles)))
  }

  def create(teamId: String): Action[AnyContent] = TeamSpecificMemberRequiredAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.create(teamRequest.team, formWithErrors))
      ),
      data => ???
    )
  }

  def view(caseKey: IssueKey): Action[AnyContent] = ???

}
