package controllers.admin

import controllers.BaseController
import controllers.admin.OwnersController._
import controllers.refiners.{CanEditCaseActionRefiner, CanEditEnquiryActionRefiner}
import domain.{CaseNoteSave, IssueKey, Member, OwnerSave}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{CaseService, EnquiryService, NotificationService, PermissionService}
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object OwnersController {
  def ownersForm(userLookupService: UserLookupService, permissionService: PermissionService, existing: Set[Member], allowEmpty: Boolean): Form[OwnerSave] = {
    val nonEmpty = Constraint { u: Seq[Usercode] =>
      if (allowEmpty || u.nonEmpty) Valid
      else Invalid("error.owners.case.nonEmpty")
    }

    /**
      * Check is each of the provided user codes in the request is a found user and in any team
      */
    val validateUsers = Constraint { usercodes: Seq[Usercode] =>
      if (usercodes.isEmpty) Valid
      else {
        val usercodesToValidate = usercodes.toSet.diff(existing.map(_.usercode)) // Only validate new usercodes
        val users = userLookupService.getUsers(usercodesToValidate.toSeq).toOption.getOrElse(Map.empty)
        val invalid = usercodesToValidate.filter(u => !users.get(u).exists(_.isFound))

        if (invalid.nonEmpty) {
          Invalid("error.userIds.invalid", Seq(invalid.map(_.string).mkString(", ")))
        } else {
          permissionService.inAnyTeam(usercodesToValidate).fold(
            serviceErrors => Invalid(serviceErrors.map { e => ValidationError(e.message) }),
            resultMap => {
              val notInAnyTeam = resultMap.filter { case (_, inAnyTeam) => !inAnyTeam }.map { case (user, _) => user }.toSeq
              if (notInAnyTeam.isEmpty) Valid
              else Invalid("error.owners.invalid", Seq(notInAnyTeam.map(_.string).mkString(", ")))
            }
          )
        }
      }
    }

    Form(
      mapping(
        "owners" -> seq(text).transform[Seq[Usercode]](_.filter(_.hasText).map(Usercode.apply), _.map(_.string))
          .verifying(nonEmpty, validateUsers),
        "message" -> optional(text)
      )(OwnerSave.apply)(OwnerSave.unapply)
    )
  }
}

@Singleton
class OwnersController @Inject()(
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner,
  userLookupService: UserLookupService,
  enquiryService: EnquiryService,
  caseService: CaseService,
  notificationService: NotificationService,
  permissionService: PermissionService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canEditCaseActionRefiner._
  import canEditEnquiryActionRefiner._

  def enquiryForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id)).successMap { owners =>
      val existingOwners = owners.getOrElse(request.enquiry.id, Set())

      Ok(views.html.admin.enquiry.owners(
        ownersForm(userLookupService, permissionService, existingOwners, allowEmpty = true)
          .fill(OwnerSave(owners.getOrElse(request.enquiry.id, Set()).map(_.usercode).toSeq.sortBy(_.string), None)),
        request.enquiry
      ))
    }
  }

  def caseForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id)).successMap { owners =>
      val existingOwners = owners.getOrElse(request.`case`.id, Set())

      Ok(views.html.admin.cases.owners(
        ownersForm(userLookupService, permissionService, existingOwners, allowEmpty = false)
          .fill(OwnerSave(owners.getOrElse(request.`case`.id, Set()).map(_.usercode).toSeq.sortBy(_.string), None)),
        request.`case`
      ))
    }
  }

  def enquirySubmit(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id)).successFlatMap { ownerMap =>
      val existingOwners = ownerMap.getOrElse(request.enquiry.id, Set())
      ownersForm(userLookupService, permissionService, existingOwners, allowEmpty = true).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry))),
        data =>
          enquiryService.setOwners(request.enquiry.id, data.usercodes.toSet).successMap(_ =>
            Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
              .flashing("success" -> Messages("flash.enquiry.owners.updated"))
          )
      )
    }
  }

  def enquirySubmitSelf(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id)).successFlatMap { ownerMap =>
      val currentOwners = ownerMap.getOrElse(request.enquiry.id, Set())
      if (currentOwners.map(_.usercode).contains(currentUser.usercode)) {
        Future.successful(Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key)))
      } else {
        enquiryService.setOwners(request.enquiry.id, currentOwners.map(_.usercode) + currentUser.usercode).successMap(_ =>
          Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
            .flashing("success" -> Messages("flash.enquiry.owners.updated"))
        )
      }
    }
  }

  def caseSubmit(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id)).successFlatMap { ownerMap =>
      val existingOwners = ownerMap.getOrElse(request.`case`.id, Set())
      ownersForm(userLookupService, permissionService, existingOwners, allowEmpty = false).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.cases.owners(formWithErrors, request.`case`))),
        data => {
          val note = data.message.map(m => CaseNoteSave(m, currentUser().usercode, None))
          caseService.setOwners(request.`case`.id, data.usercodes.toSet, note).successFlatMap { setOwnersResult =>
            notificationService.newCaseOwner(setOwnersResult.added.map(_.userId).toSet, request.`case`).successMap(_ =>
              Redirect(controllers.admin.routes.CaseController.view(request.`case`.key))
                .flashing("success" -> Messages("flash.case.owners.updated"))
            )
          }
        }
      )
    }
  }

  def caseSubmitSelf(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id)).successFlatMap { ownerMap =>
      val currentOwners = ownerMap.getOrElse(request.`case`.id, Set())
      ownersForm(userLookupService, permissionService, currentOwners, allowEmpty = true).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.cases.owners(formWithErrors, request.`case`))),
        data => {
          if (currentOwners.map(_.usercode).contains(currentUser.usercode)) {
            Future.successful(Redirect(controllers.admin.routes.CaseController.view(request.`case`.key)))
          } else {
            val note = data.message.map(m => CaseNoteSave(m, currentUser().usercode, None))
            caseService.setOwners(request.`case`.id, currentOwners.map(_.usercode) + currentUser.usercode, note).successMap { _ =>
              Redirect(controllers.admin.routes.CaseController.view(request.`case`.key))
                .flashing("success" -> Messages("flash.case.owners.updated"))
            }
          }
        }
      )
    }
  }

}