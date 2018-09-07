package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.admin.CaseController._
import controllers.refiners.{CanEditCaseActionRefiner, CanViewCaseActionRefiner, CanViewTeamActionRefiner, CaseSpecificRequest}
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults.ServiceError
import helpers.{FormHelpers, JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.{CaseService, EnquiryService}
import services.tabula.ProfileService
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object CaseController {
  case class CaseFormData(
    clients: Set[UniversityID],
    subject: String,
    incidentDate: OffsetDateTime,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
    cause: CaseCause,
    caseType: Option[CaseType],
    tags: Set[CaseTag],
    originalEnquiry: Option[UUID],
    version: Option[OffsetDateTime]
  )

  def form(team: Team, profileService: ProfileService, enquiryService: EnquiryService, existingVersion: Option[OffsetDateTime] = None)(implicit t: TimingContext, executionContext: ExecutionContext): Form[CaseFormData] = {
    def isValid(u: UniversityID): Boolean =
      Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
        .toOption.exists(_.isRight)

    def isValidEnquiry(id: UUID): Boolean =
      Try(Await.result(enquiryService.get(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(mapping(
      "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u))).verifying("error.required", _.exists(_.string.nonEmpty)),
      "subject" -> nonEmptyText(maxLength = Case.SubjectMaxLength),
      "incidentDate" -> FormHelpers.offsetDateTime,
      "onCampus" -> boolean,
      "notifiedPolice" -> boolean,
      "notifiedAmbulance" -> boolean,
      "notifiedFire" -> boolean,
      "cause" -> CaseCause.formField,
      "caseType" -> optional(CaseType.formField).verifying("error.caseType.invalid", t => (CaseType.valuesFor(team).isEmpty && t.isEmpty) || t.exists(CaseType.valuesFor(team).contains)),
      "tags" -> set(CaseTag.formField),
      "originalEnquiry" -> optional(uuid.verifying("error.required", id => isValidEnquiry(id))),
      "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
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

  case class CaseNoteFormData(
    text: String,
    version: OffsetDateTime
  )

  def caseNoteForm(version: OffsetDateTime): Form[CaseNoteFormData] = Form(mapping(
    "text" -> nonEmptyText,
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  )(CaseNoteFormData.apply)(CaseNoteFormData.unapply))

  def deleteNoteForm(version: OffsetDateTime): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  ))
}

@Singleton
class CaseController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  enquiries: EnquiryService,
  userLookupService: UserLookupService,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canViewCaseActionRefiner._
  import canViewTeamActionRefiner._
  import canEditCaseActionRefiner._

  private def renderCase(caseKey: IssueKey, caseNoteForm: Form[CaseNoteFormData])(implicit request: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.findFull(caseKey).successMap { c =>
      val usercodes = c.notes.map(_.teamMember).distinct
      val userLookup = userLookupService.getUsers(usercodes).toOption.getOrElse(Map())
      val caseNotes = c.notes.map { note => (note, userLookup.get(note.teamMember)) }

      Ok(views.html.admin.cases.view(c, caseNotes, caseNoteForm))
    }

  def view(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    renderCase(caseKey, caseNoteForm(caseRequest.`case`.version).fill(CaseNoteFormData("", caseRequest.`case`.version)))
  }

  def createForm(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId) { implicit teamRequest =>
    Ok(views.html.admin.cases.create(teamRequest.team, form(teamRequest.team, profiles, enquiries)))
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles, enquiries).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.create(teamRequest.team, formWithErrors))
      ),
      data => {
        val c = Case(
          id = None, // Set by service
          key = None, // Set by service
          subject = data.subject,
          created = JavaTime.offsetDateTime,
          incidentDate = data.incidentDate,
          team = teamRequest.team,
          version = JavaTime.offsetDateTime,
          state = IssueState.Open,
          onCampus = data.onCampus,
          notifiedPolice = data.notifiedPolice,
          notifiedAmbulance = data.notifiedAmbulance,
          notifiedFire = data.notifiedFire,
          originalEnquiry = data.originalEnquiry,
          caseType = data.caseType,
          cause = data.cause
        )

        val clients = data.clients.filter(_.string.nonEmpty)

        cases.create(c, clients, data.tags).successMap { created =>
          Redirect(controllers.admin.routes.CaseController.view(created.key.get))
            .flashing("success" -> Messages("flash.case.created", created.key.get.string))
        }
      }
    )
  }

  def editForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    val clientCase = caseRequest.`case`

    ServiceResults.zip(
      cases.getClients(clientCase.id.get),
      cases.getCaseTags(clientCase.id.get)
    ).successMap { case (clients, tags) =>
      Ok(
        views.html.admin.cases.edit(
          clientCase,
          form(clientCase.team, profiles, enquiries, Some(clientCase.version))
            .fill(CaseFormData(
              clients,
              clientCase.subject,
              clientCase.incidentDate,
              clientCase.onCampus,
              clientCase.notifiedPolice,
              clientCase.notifiedAmbulance,
              clientCase.notifiedFire,
              clientCase.cause,
              clientCase.caseType,
              tags,
              clientCase.originalEnquiry,
              Some(clientCase.version)
            ))
        )
      )
    }
  }

  def edit(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    val clientCase = caseRequest.`case`

    form(clientCase.team, profiles, enquiries, Some(clientCase.version)).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(
          views.html.admin.cases.edit(
            clientCase,
            formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", clientCase.version))
          )
        )
      ),
      data => {
        val c = Case(
          id = clientCase.id,
          key = clientCase.key,
          subject = data.subject,
          created = clientCase.created,
          incidentDate = data.incidentDate,
          team = clientCase.team,
          version = JavaTime.offsetDateTime,
          state = clientCase.state,
          onCampus = data.onCampus,
          notifiedPolice = data.notifiedPolice,
          notifiedAmbulance = data.notifiedAmbulance,
          notifiedFire = data.notifiedFire,
          originalEnquiry = data.originalEnquiry,
          caseType = data.caseType,
          cause = data.cause
        )

        val clients = data.clients.filter(_.string.nonEmpty)

        cases.update(c, clients, data.tags, clientCase.version).successMap { updated =>
          Redirect(controllers.admin.routes.CaseController.view(updated.key.get))
            .flashing("success" -> Messages("flash.case.updated"))
        }
      }
    )
  }

  def linkForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.link(caseRequest.`case`, caseLinkForm(caseRequest.`case`.id.get, cases)))
  }

  def link(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseLinkForm(caseRequest.`case`.id.get, cases).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.link(caseRequest.`case`, formWithErrors))
      ),
      data => cases.find(data.targetID).successFlatMap { targetCase =>
        val caseNote = CaseNoteSave(s"${caseKey.string} (${caseRequest.`case`.subject}) ${data.linkType.outwardDescription} ${targetCase.key.get.string} (${targetCase.subject})", caseRequest.context.user.get.usercode)

        cases.addLink(data.linkType, caseRequest.`case`.id.get, data.targetID, caseNote).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.linked"))
        }
      }
    )
  }

  def addNote(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseNoteForm(caseRequest.`case`.version).bindFromRequest().fold(
      formWithErrors => renderCase(caseKey, formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", caseRequest.`case`.version))),
      data =>
        // We don't do anything with data.version here, it's validated but we don't lock the case when adding a general note
        cases.addGeneralNote(caseRequest.`case`.id.get, CaseNoteSave(data.text, caseRequest.context.user.get.usercode)).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.noteAdded"))
        }
    )
  }

  def close(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseNoteForm(caseRequest.`case`.version).bindFromRequest().fold(
      formWithErrors => renderCase(caseKey, formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", caseRequest.`case`.version))),
      data => {
        val caseNote = CaseNoteSave(data.text, caseRequest.context.user.get.usercode)

        cases.updateState(caseRequest.`case`.id.get, IssueState.Closed, data.version, caseNote).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.closed"))
        }
      }
    )
  }

  def reopen(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseNoteForm(caseRequest.`case`.version).bindFromRequest().fold(
      formWithErrors => renderCase(caseKey, formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", caseRequest.`case`.version))),
      data => {
        val caseNote = CaseNoteSave(data.text, caseRequest.context.user.get.usercode)

        cases.updateState(caseRequest.`case`.id.get, IssueState.Reopened, data.version, caseNote).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.reopened"))
        }
      }
    )
  }

  def editNoteForm(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    withCaseNote(id) { note =>
      Future.successful(
        Ok(
          views.html.admin.cases.editNote(
            caseKey,
            note,
            caseNoteForm(note.lastUpdated).fill(CaseNoteFormData(note.text, note.lastUpdated))
          )
        )
      )
    }
  }

  def editNote(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    withCaseNote(id) { note =>
      caseNoteForm(note.lastUpdated).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(
            views.html.admin.cases.editNote(
              caseKey,
              note,
              formWithErrors.bind(formWithErrors.data ++ JavaTime.OffsetDateTimeFormatter.unbind("version", note.lastUpdated))
            )
          )
        ),
        data =>
          cases.updateNote(caseRequest.`case`.id.get, note.id, CaseNoteSave(data.text, caseRequest.context.user.get.usercode), data.version).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.noteUpdated"))
          }
      )
    }
  }

  def deleteNote(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    withCaseNote(id) { note =>
      deleteNoteForm(note.lastUpdated).bindFromRequest().fold(
        formWithErrors => Future.successful(
          // Nowhere to show a validation error so just fall back to an error page
          showErrors(formWithErrors.errors.map { e => ServiceError(e.format) })
        ),
        version =>
          cases.deleteNote(caseRequest.`case`.id.get, note.id, version).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.noteDeleted"))
          }
      )
    }
  }

  private def withCaseNote(id: UUID)(f: CaseNote => Future[Result])(implicit caseRequest: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.getNotes(caseRequest.`case`.id.get).successFlatMap { notes =>
      notes.find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

}
