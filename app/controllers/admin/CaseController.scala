package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.{BaseController, UploadedFileControllerHelper}
import controllers.admin.CaseController._
import controllers.refiners.{CanEditCaseActionRefiner, _}
import domain._
import domain.dao.CaseDao.Case
import domain.CaseNoteType._
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.{FormHelpers, JavaTime, ServiceResults}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.tabula.ProfileService
import services._
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object CaseController {
  case class CaseIncidentFormData(
    incidentDate: OffsetDateTime,
    onCampus: Boolean,
    notifiedPolice: Boolean,
    notifiedAmbulance: Boolean,
    notifiedFire: Boolean,
  )

  case class CaseFormData(
    clients: Set[UniversityID],
    subject: String,
    incident: Option[CaseIncidentFormData],
    cause: CaseCause,
    caseType: Option[CaseType],
    tags: Set[CaseTag],
    originalEnquiry: Option[UUID],
    version: Option[OffsetDateTime]
  )

  def form(
    team: Team,
    profileService: ProfileService,
    enquiryService: EnquiryService,
    existingClients: Set[Client],
    existingVersion: Option[OffsetDateTime] = None
  )(implicit t: TimingContext, executionContext: ExecutionContext): Form[CaseFormData] = {
    def isValid(u: UniversityID, existing: Set[Client]): Boolean =
      existing.exists(_.universityID == u) ||
        Try(Await.result(profileService.getProfile(u).map(_.value), 5.seconds))
          .toOption.exists(_.exists(_.nonEmpty))

    def isValidEnquiry(id: UUID): Boolean =
      Try(Await.result(enquiryService.get(id), 5.seconds))
        .toOption.exists(_.isRight)

    Form(
      mapping(
        "clients" -> set(text.transform[UniversityID](UniversityID.apply, _.string).verifying("error.client.invalid", u => u.string.isEmpty || isValid(u, existingClients))).verifying("error.required", _.exists(_.string.nonEmpty)),
        "subject" -> nonEmptyText(maxLength = Case.SubjectMaxLength),
        "incident" -> optional(mapping(
          "incidentDate" -> FormHelpers.offsetDateTime,
          "onCampus" -> boolean,
          "notifiedPolice" -> boolean,
          "notifiedAmbulance" -> boolean,
          "notifiedFire" -> boolean,
        )(CaseIncidentFormData.apply)(CaseIncidentFormData.unapply)),
        "cause" -> CaseCause.formField,
        "caseType" -> optional(CaseType.formField).verifying("error.caseType.invalid", t => (CaseType.valuesFor(team).isEmpty && t.isEmpty) || t.exists(CaseType.valuesFor(team).contains)),
        "tags" -> set(CaseTag.formField),
        "originalEnquiry" -> optional(uuid.verifying("error.required", id => isValidEnquiry(id))),
        "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
      )(CaseFormData.apply)(CaseFormData.unapply)
    )
  }

  case class CaseLinkFormData(
    linkType: CaseLinkType,
    targetID: UUID,
    message: String
  )

  private def isValid(id: UUID, caseService: CaseService)(implicit t: TimingContext): Boolean =
    Try(Await.result(caseService.find(id), 5.seconds))
      .toOption.exists(_.isRight)

  def caseLinkForm(sourceID: UUID, caseService: CaseService)(implicit t: TimingContext): Form[CaseLinkFormData] = {
    Form(mapping(
      "linkType" -> CaseLinkType.formField,
      "targetID" -> uuid.verifying("error.linkTarget.same", _ != sourceID).verifying("error.required", id => isValid(id, caseService)),
      "message" -> nonEmptyText
    )(CaseLinkFormData.apply)(CaseLinkFormData.unapply))
  }

  val generalNoteTypes = Seq(GeneralNote, CaseClosed, CaseReopened)

  case class CaseNoteFormData(
    text: String,
    version: OffsetDateTime
  )

  def caseNoteForm(version: OffsetDateTime): Form[CaseNoteFormData] = Form(mapping(
    "text" -> nonEmptyText,
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  )(CaseNoteFormData.apply)(CaseNoteFormData.unapply))

  def caseNoteFormPrefilled(version: OffsetDateTime): Form[CaseNoteFormData] =
    caseNoteForm(version).fill(CaseNoteFormData("", version))

  def deleteForm(version: OffsetDateTime): Form[OffsetDateTime] = Form(single(
    "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == version)
  ))

  case class ReassignCaseData(
    team: Team,
    caseType: Option[CaseType],
    version: OffsetDateTime,
    message: String
  )

  def caseReassignForm(clientCase: Case) = Form(
    mapping(
      "team" -> Teams.formField,
      "caseType" -> optional(CaseType.formField),
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == clientCase.version),
      "message" -> nonEmptyText
    )(ReassignCaseData.apply)(ReassignCaseData.unapply)
  )
}

@Singleton
class CaseController @Inject()(
  profiles: ProfileService,
  cases: CaseService,
  enquiries: EnquiryService,
  appointments: AppointmentService,
  userLookupService: UserLookupService,
  permissions: PermissionService,
  clientService: ClientService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  canViewTeamActionRefiner: CanViewTeamActionRefiner,
  canViewCaseActionRefiner: CanViewCaseActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner,
  canEditCaseNoteActionRefiner: CanEditCaseNoteActionRefiner,
  uploadedFileControllerHelper: UploadedFileControllerHelper
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._
  import canEditCaseActionRefiner._
  import canViewCaseActionRefiner._
  import canViewTeamActionRefiner._
  import canEditCaseNoteActionRefiner._
  import CaseMessageController.messageForm

  def renderCase(caseKey: IssueKey, messageForm: Form[String])(implicit request: CaseSpecificRequest[_]): Future[Result] = {
    val fetchOriginalEnquiry: Future[ServiceResult[Option[Enquiry]]] =
      request.`case`.originalEnquiry.map { enquiryId =>
        enquiries.get(enquiryId).map(_.right.map(Some(_)))
      }.getOrElse(Future.successful(Right(None)))

    ServiceResults.zip(
      cases.findForView(caseKey),
      cases.getClients(request.`case`.id.get),
      cases.getCaseTags(request.`case`.id.get),
      cases.getNotes(request.`case`.id.get),
      cases.getOwners(Set(request.`case`.id.get)).map(_.right.map(_.getOrElse(request.`case`.id.get, Set.empty))),
      fetchOriginalEnquiry,
      cases.getHistory(request.`case`.id.get)
    ).successFlatMap { case (c, clients, tags, notes, owners, originalEnquiry, history) =>
      val sectionNotes = notes.filterNot(note => generalNoteTypes.contains(note.noteType))
      val sectionNotesByType = sectionNotes.groupBy(_.noteType)

      profiles.getProfiles(clients.map(_.universityID)).successMap { clientProfiles =>
        Ok(views.html.admin.cases.view(
          c,
          clients.toSeq.distinct.map(client => client -> clientProfiles.get(client.universityID)).toMap,
          tags,
          owners,
          sectionNotesByType,
          originalEnquiry,
          history
        ))
      }
    }
  }

  private def renderCase()(implicit request: CaseSpecificRequest[_]): Future[Result] = {
    import request.{`case` => c}
    renderCase(
      c.key.get,
      messageForm
    )
  }

  def view(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    renderCase()
  }

  def links(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    cases.getLinks(caseRequest.`case`.id.get).successMap { case (outgoing, incoming) =>
      ServiceResults.sequence(outgoing.map(c => EntityAndCreator(c, permissions))).flatMap(o =>
        ServiceResults.sequence(incoming.map(c => EntityAndCreator(c, permissions))).map(i => (o, i))
      ).fold(
        errors => showErrors(errors),
        {
          case (outgoingCaseLinks, incomingCaseLinks) =>
            Ok(views.html.admin.cases.sections.links(
              c = caseRequest.`case`,
              outgoing = outgoingCaseLinks,
              incoming = incomingCaseLinks
            ))
        }
      )
    }
  }

  def documents(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    cases.getDocuments(caseRequest.`case`.id.get).successMap(documents =>
      ServiceResults.sequence(documents.map(c => EntityAndCreator(c, permissions))).fold(
        errors => showErrors(errors),
        docs => Ok(views.html.admin.cases.sections.documents(caseRequest.`case`, docs))
      )
    )
  }

  def appointments(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    appointments.findForCase(caseRequest.`case`.id.get).successMap(a =>
      Ok(views.html.admin.cases.sections.appointments(a))
    )
  }

  def notes(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    cases.getNotes(caseRequest.`case`.id.get).successMap(notes =>
      Ok(views.html.admin.cases.sections.notes(
        caseRequest.`case`,
        notes.filter(note => generalNoteTypes.contains(note.noteType)),
        caseNoteFormPrefilled(caseRequest.`case`.version)
      ))
    )
  }

  def messages(caseKey: IssueKey): Action[AnyContent] = CanViewCaseAction(caseKey).async { implicit caseRequest =>
    cases.getClients(caseRequest.`case`.id.get).successFlatMap(caseClients =>
      ServiceResults.zip(
        cases.getCaseMessages(caseRequest.`case`.id.get),
        profiles.getProfiles(caseClients.map(_.universityID))
      ).successMap { case (messages, p) =>
        Ok(views.html.admin.cases.sections.messages(
          caseRequest.`case`,
          messages,
          caseClients.map(c => c -> p.get(c.universityID)).toMap,
          messageForm,
          uploadedFileControllerHelper.supportedMimeTypes
        ))
      }
    )
  }

  def createSelectTeam(fromEnquiry: Option[IssueKey], client: Option[UniversityID]): Action[AnyContent] = AnyTeamMemberRequiredAction { implicit request =>
    permissions.teams(request.context.user.get.usercode).fold(showErrors, teams => {
      if (teams.size == 1)
        Redirect(controllers.admin.routes.CaseController.createForm(teams.head.id, fromEnquiry, client))
      else
        Ok(views.html.admin.cases.createSelectTeam(teams, fromEnquiry, client))
    })
  }

  def createForm(teamId: String, fromEnquiry: Option[IssueKey], client: Option[UniversityID]): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    val baseForm = form(teamRequest.team, profiles, enquiries, Set())

    (fromEnquiry, client) match {
      case (Some(_), Some(_)) => Future.successful(
        BadRequest("Can't specify both fromEnquiry and client")
      )

      case (Some(enquiryKey), _) => enquiries.get(enquiryKey).successMap { enquiry =>
        Ok(views.html.admin.cases.create(
          teamRequest.team,
          baseForm.bind(Map(
            "clients[0]" -> enquiry.client.universityID.string,
            "originalEnquiry" -> enquiry.id.get.toString
          )).discardingErrors
        ))
      }

      case (_, Some(universityID)) => Future.successful(
        Ok(views.html.admin.cases.create(
          teamRequest.team,
          baseForm.bind(Map(
            "clients[0]" -> universityID.string
          )).discardingErrors
        ))
      )

      case _ => Future.successful(
        Ok(views.html.admin.cases.create(teamRequest.team, baseForm))
      )
    }
  }

  def create(teamId: String): Action[AnyContent] = CanViewTeamAction(teamId).async { implicit teamRequest =>
    form(teamRequest.team, profiles, enquiries, Set()).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.create(teamRequest.team, formWithErrors))
      ),
      data => {
        val c = Case(
          id = None, // Set by service
          key = None, // Set by service
          subject = data.subject,
          created = JavaTime.offsetDateTime,
          team = teamRequest.team,
          version = JavaTime.offsetDateTime,
          state = IssueState.Open,
          incidentDate = data.incident.map(_.incidentDate),
          onCampus = data.incident.map(_.onCampus),
          notifiedPolice = data.incident.map(_.notifiedPolice),
          notifiedAmbulance = data.incident.map(_.notifiedAmbulance),
          notifiedFire = data.incident.map(_.notifiedFire),
          originalEnquiry = data.originalEnquiry,
          caseType = data.caseType,
          cause = data.cause
        )

        val clients = data.clients.filter(_.string.nonEmpty)

        val updateOriginalEnquiry: Future[ServiceResult[Option[Enquiry]]] = data.originalEnquiry.map { enquiryId =>
          enquiries.get(enquiryId).flatMap(_.fold(
            errors => Future.successful(Left(errors)),
            enquiry =>
              enquiries.updateState(enquiryId, IssueState.Closed, enquiry.lastUpdated).map(_.right.map(Some(_)))
          ))
        }.getOrElse(Future.successful(Right(None)))

        ServiceResults.zip(
          cases.create(c, clients, data.tags),
          updateOriginalEnquiry
        ).successFlatMap { case (createdCase, originalEnquiry) =>
          val setOwners: Future[ServiceResult[Set[Member]]] =
            originalEnquiry.map { enquiry =>
              enquiries.getOwners(Set(enquiry.id.get)).flatMap(_.fold(
                errors => Future.successful(Left(errors)),
                ownerMap => {
                  // CASE-133 Add the creating user as an owner
                  val owners = ownerMap.getOrElse(enquiry.id.get, Set.empty).map(_.usercode) + teamRequest.context.user.get.usercode
                  cases.setOwners(createdCase.id.get, owners)
                }
              ))
            }.getOrElse {
              // CASE-133 Add the creating user as an owner
              cases.setOwners(createdCase.id.get, Set(teamRequest.context.user.get.usercode))
            }

          setOwners.successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(createdCase.key.get))
              .flashing("success" -> Messages("flash.case.created", createdCase.key.get.string))
          }
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
          form(clientCase.team, profiles, enquiries, clients, Some(clientCase.version))
            .fill(CaseFormData(
              clients.map(_.universityID),
              clientCase.subject,
              clientCase.incidentDate.map { incidentDate =>
                CaseIncidentFormData(
                  incidentDate,
                  clientCase.onCampus.get,
                  clientCase.notifiedPolice.get,
                  clientCase.notifiedAmbulance.get,
                  clientCase.notifiedFire.get,
                )
              },
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

    cases.getClients(clientCase.id.get).successFlatMap(clients =>
      form(clientCase.team, profiles, enquiries, clients, Some(clientCase.version)).bindFromRequest().fold(
        formWithErrors => Future.successful(
          Ok(
            views.html.admin.cases.edit(
              clientCase,
              formWithErrors.withVersion(clientCase.version)
            )
          )
        ),
        data => {
          val c = Case(
            id = clientCase.id,
            key = clientCase.key,
            subject = data.subject,
            created = clientCase.created,
            team = clientCase.team,
            version = JavaTime.offsetDateTime,
            state = clientCase.state,
            incidentDate = data.incident.map(_.incidentDate),
            onCampus = data.incident.map(_.onCampus),
            notifiedPolice = data.incident.map(_.notifiedPolice),
            notifiedAmbulance = data.incident.map(_.notifiedAmbulance),
            notifiedFire = data.incident.map(_.notifiedFire),
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
        val caseNote = CaseNoteSave(data.message, caseRequest.context.user.get.usercode)

        cases.addLink(data.linkType, caseRequest.`case`.id.get, targetCase.id.get, caseNote).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.linked"))
        }
      }
    )
  }

  def deleteLink(caseKey: IssueKey, linkId: UUID): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    withCaseLink(linkId) { link =>
      deleteForm(link.updatedDate).bindFromRequest().fold(
        formWithErrors => Future.successful(
          // Nowhere to show a validation error so just fall back to an error page
          showErrors(formWithErrors.errors.map { e => ServiceError(e.format) })
        ),
        version =>
          cases.deleteLink(caseRequest.`case`.id.get, linkId, version).successMap { _ =>
            Redirect(controllers.admin.routes.CaseController.view(caseKey))
              .flashing("success" -> Messages("flash.case.linkDeleted"))
          }
      )
    }
  }

  private def withCaseLink(id: UUID)(f: CaseLink => Future[Result])(implicit caseRequest: CaseSpecificRequest[AnyContent]): Future[Result] =
    cases.getLinks(caseRequest.`case`.id.get).successFlatMap { case (outgoing, incoming) =>
      (outgoing ++ incoming).find(_.id == id).map(f)
        .getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }

  def addNote(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseNoteForm(caseRequest.`case`.version).bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(formWithErrors.errors.mkString(", "))),
      data =>
        // We don't do anything with data.version here, it's validated but we don't lock the case when adding a general note
        cases.addGeneralNote(caseRequest.`case`.id.get, CaseNoteSave(data.text, caseRequest.context.user.get.usercode)).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.noteAdded"))
        }
    )
  }

  def closeForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.close(caseRequest.`case`, caseNoteForm(caseRequest.`case`.version).fill(CaseNoteFormData("", caseRequest.`case`.version))))
  }

  def close(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseNoteForm(caseRequest.`case`.version).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.close(caseRequest.`case`, formWithErrors))
      ),
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
      formWithErrors => Future.successful(BadRequest(formWithErrors.errors.mkString(", "))),
      data => {
        val caseNote = CaseNoteSave(data.text, caseRequest.context.user.get.usercode)

        cases.updateState(caseRequest.`case`.id.get, IssueState.Reopened, data.version, caseNote).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.reopened"))
        }
      }
    )
  }

  def editNoteForm(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseNoteAction(id).async { implicit noteRequest =>
    val note = noteRequest.note
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

  def editNote(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseNoteAction(id).async { implicit noteRequest =>
    caseNoteForm(noteRequest.note.lastUpdated).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(
          views.html.admin.cases.editNote(
            caseKey,
            noteRequest.note,
            formWithErrors.withVersion(noteRequest.note.lastUpdated)
          )
        )
      ),
      data =>
        cases.updateNote(noteRequest.`case`.id.get, noteRequest.note.id, CaseNoteSave(data.text, noteRequest.context.user.get.usercode), data.version).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.noteUpdated"))
        }
    )
  }

  def deleteNote(caseKey: IssueKey, id: UUID): Action[AnyContent] = CanEditCaseNoteAction(id).async { implicit noteRequest =>
    deleteForm(noteRequest.note.lastUpdated).bindFromRequest().fold(
      formWithErrors => Future.successful(
        // Nowhere to show a validation error so just fall back to an error page
        showErrors(formWithErrors.errors.map { e => ServiceError(e.format) })
      ),
      version =>
        cases.deleteNote(noteRequest.`case`.id.get, noteRequest.note.id, version).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(caseKey))
            .flashing("success" -> Messages("flash.case.noteDeleted"))
        }
    )
  }

  def reassignForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey) { implicit caseRequest =>
    Ok(views.html.admin.cases.reassign(caseRequest.`case`, caseReassignForm(caseRequest.`case`).fill(ReassignCaseData(
      team = caseRequest.`case`.team,
      caseType = caseRequest.`case`.caseType,
      version = caseRequest.`case`.version,
      message = null
    ))))
  }

  def reassign(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit caseRequest =>
    caseReassignForm(caseRequest.`case`).bindFromRequest().fold(
      formWithErrors => Future.successful(
        Ok(views.html.admin.cases.reassign(caseRequest.`case`, formWithErrors))
      ),
      data =>
        if (!(CaseType.valuesFor(data.team).isEmpty && data.caseType.isEmpty) && !data.caseType.exists(CaseType.valuesFor(data.team).contains)) {
          Future.successful(
            Ok(views.html.admin.cases.reassign(
              caseRequest.`case`,
              caseReassignForm(caseRequest.`case`).fill(data).withError("caseType", "error.caseType.invalid")
            ))
          )
        } else {
          if (data.team == caseRequest.`case`.team) // No change
            Future.successful(Redirect(controllers.admin.routes.AdminController.teamHome(data.team.id).withFragment("cases")))
          else
            cases.reassign(caseRequest.`case`, data.team, data.caseType, CaseNoteSave(data.message, caseRequest.context.user.get.usercode), data.version).successMap { _ =>
              Redirect(controllers.admin.routes.AdminController.teamHome(caseRequest.`case`.team.id).withFragment("cases"))
                .flashing("success" -> Messages("flash.case.reassigned", data.team.name))
            }
        }
    )
  }

}
