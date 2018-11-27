package controllers.admin

import controllers.BaseController
import controllers.admin.OwnersController._
import controllers.refiners.{CanEditCaseActionRefiner, CanEditEnquiryActionRefiner}
import domain.{CaseNoteSave, IssueKey, Member, OwnerSave}
import helpers.ServiceResults.ServiceResult
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Request}
import services.{CaseService, EnquiryService, NotificationService, PermissionService}
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object OwnersController {
  val ownersForm: Form[OwnerSave] = Form(
    mapping(
      "owners" -> seq(text).transform[Seq[Usercode]](_.filter(_.hasText).map(Usercode.apply), _.map(_.string)),
      "message" -> optional(text)
    )(OwnerSave.apply)(OwnerSave.unapply)
  )
}

@Singleton
class OwnersController @Inject()(
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  canEditCaseActionRefiner: CanEditCaseActionRefiner,
  userLookupService: UserLookupService,
  enquiryService: EnquiryService,
  caseService: CaseService,
  notificationService: NotificationService,
  permissionService: PermissionService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canEditCaseActionRefiner._
  import canEditEnquiryActionRefiner._

  def enquiryForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id)).successMap(owners =>
      Ok(views.html.admin.enquiry.owners(
        ownersForm.fill(OwnerSave(owners.getOrElse(request.enquiry.id, Set()).map(_.usercode).toSeq.sortBy(_.string), None)),
        request.enquiry
      ))
    )
  }

  def caseForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id)).successMap(owners =>
      Ok(views.html.admin.cases.owners(
        ownersForm.fill(OwnerSave(owners.getOrElse(request.`case`.id, Set()).map(_.usercode).toSeq.sortBy(_.string), None)),
        request.`case`
      ))
    )
  }

  def enquirySubmit(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id)).successFlatMap(previousOwners =>
      bindAndVerifyOwners(previousOwners.getOrElse(request.enquiry.id, Set()), ownersForm, allowEmpty = true).fold(
        errors => Future.successful(showErrors(errors)),
        form => form.fold(
          formWithErrors => {
            Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
          },
          data => {
            enquiryService.setOwners(request.enquiry.id, data.usercodes.toSet).successMap(_ =>
              Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
                .flashing("success" -> Messages("flash.enquiry.owners.updated"))
            )
          }
        )
      )
    )
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
    caseService.getOwners(Set(request.`case`.id)).successFlatMap(previousOwners =>
      bindAndVerifyOwners(previousOwners.getOrElse(request.`case`.id, Set()), ownersForm, allowEmpty = false).fold(
        errors => Future.successful(showErrors(errors)),
        form => {
          form.fold(
            formWithErrors => {
              Future.successful(Ok(views.html.admin.cases.owners(formWithErrors, request.`case`)))
            },
            data => {
              val note = data.message.map(m => CaseNoteSave(m, currentUser().usercode))
              caseService.setOwners(request.`case`.id, data.usercodes.toSet, note).successFlatMap { setOwnersResult =>
                notificationService.newCaseOwner(setOwnersResult.added.map(_.userId).toSet, request.`case`).successMap(_ =>
                  Redirect(controllers.admin.routes.CaseController.view(request.`case`.key))
                    .flashing("success" -> Messages("flash.case.owners.updated"))
                )
              }
            }
          )
        }
      )
    )
  }

  def caseSubmitSelf(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id)).successFlatMap { ownerMap =>
      ownersForm.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(Ok(views.html.admin.cases.owners(formWithErrors, request.`case`)))
        },
        data => {
          val currentOwners = ownerMap.getOrElse(request.`case`.id, Set())
          if (currentOwners.map(_.usercode).contains(currentUser.usercode)) {
            Future.successful(Redirect(controllers.admin.routes.CaseController.view(request.`case`.key)))
          } else {
            val note = data.message.map(m => CaseNoteSave(m, currentUser().usercode))
            caseService.setOwners(request.`case`.id, currentOwners.map(_.usercode) + currentUser.usercode, note).successMap { _ =>
              Redirect(controllers.admin.routes.CaseController.view(request.`case`.key))
                .flashing("success" -> Messages("flash.case.owners.updated"))
            }
          }
        }
      )
    }
  }

  /**
    * Check is each of the provided user codes in the request is a found user and in any team
    */
  private def bindAndVerifyOwners(existing: Set[Member], baseForm: Form[OwnerSave], allowEmpty: Boolean)(implicit request: Request[_]): ServiceResult[Form[OwnerSave]] = {
    baseForm.bindFromRequest.fold(
      formWithErrors => Right(formWithErrors),
      data => {
        if (data.usercodes.nonEmpty || allowEmpty) {
          val usercodesToValidate = data.usercodes.toSet.diff(existing.map(_.usercode)) // Only validate new usercodes
          val users = userLookupService.getUsers(usercodesToValidate.toSeq).toOption.getOrElse(Map.empty)
          val invalid = usercodesToValidate.filter(u => !users.get(u).exists(_.isFound))
          if (invalid.nonEmpty) {
            Right(
              baseForm.fill(data)
                .withError(FormError("owners", "error.userIds.invalid", Seq(invalid.map(_.string).mkString(", "))))
            )
          } else {
            permissionService.inAnyTeam(usercodesToValidate).fold(
              serviceErrors => Left(serviceErrors),
              resultMap => {
                val notInAnyTeam = resultMap.filter { case (_, inAnyTeam) => !inAnyTeam }.map { case (user, _) => user }.toSeq
                if (notInAnyTeam.isEmpty) {
                  Right(baseForm.fill(data))
                } else {
                  Right(
                    baseForm.fill(data)
                      .withError(FormError("owners", "error.owners.invalid", Seq(notInAnyTeam.map(_.string).mkString(", "))))
                  )
                }
              }
            )
          }
        } else {
          Right(baseForm.fill(OwnerSave(existing.map(_.usercode).toSeq, data.message)).withGlobalError("error.owners.case.nonEmpty"))
        }
      }
    )
  }

}