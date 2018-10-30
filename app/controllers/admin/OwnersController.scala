package controllers.admin

import controllers.BaseController
import controllers.refiners.{CanEditCaseActionRefiner, CanEditEnquiryActionRefiner}
import domain.{IssueKey, Member}
import helpers.ServiceResults.ServiceResult
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Forms.{seq, single, text}
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Request}
import services.{CaseService, EnquiryService, NotificationService, PermissionService}
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

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

  val ownersForm = Form(single("owners" -> seq(text).transform[Seq[Usercode]](_.filter(_.hasText).map(Usercode.apply), _.map(_.string))))

  import canEditCaseActionRefiner._
  import canEditEnquiryActionRefiner._

  def enquiryForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id.get)).successMap(owners =>
      Ok(views.html.admin.enquiry.owners(
        ownersForm.fill(owners.getOrElse(request.enquiry.id.get, Set()).map(_.usercode).toSeq.sortBy(_.string)),
        request.enquiry
      ))
    )
  }

  def caseForm(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id.get)).successMap(owners =>
      Ok(views.html.admin.cases.owners(
        ownersForm.fill(owners.getOrElse(request.`case`.id.get, Set()).map(_.usercode).toSeq.sortBy(_.string)),
        request.`case`
      ))
    )
  }

  def enquirySubmit(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id.get)).successFlatMap(previousOwners =>
      bindAndVerifyOwners(previousOwners.getOrElse(request.enquiry.id.get, Set()), allowEmpty = true).fold(
        errors => Future.successful(showErrors(errors)),
        form => form.fold(
          formWithErrors => {
            Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
          },
          data => {
            enquiryService.setOwners(request.enquiry.id.get, data.toSet).successMap(_ =>
              Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
                .flashing("success" -> Messages("flash.enquiry.owners.updated"))
            )
          }
        )
      )
    )
  }

  def enquirySubmitSelf(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id.get)).successFlatMap { ownerMap =>
      val currentOwners = ownerMap.getOrElse(request.enquiry.id.get, Set())
      if (currentOwners.map(_.usercode).contains(currentUser.usercode)) {
        Future.successful(Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key)))
      } else {
        enquiryService.setOwners(request.enquiry.id.get, currentOwners.map(_.usercode) + currentUser.usercode).successMap(_ =>
          Redirect(controllers.admin.routes.TeamEnquiryController.messages(request.enquiry.key))
            .flashing("success" -> Messages("flash.enquiry.owners.updated"))
        )
      }
    }
  }

  def caseSubmit(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id.get)).successFlatMap(previousOwners =>
      bindAndVerifyOwners(previousOwners.getOrElse(request.`case`.id.get, Set()), allowEmpty = false).fold(
        errors => Future.successful(showErrors(errors)),
        form => form.fold(
          formWithErrors => {
            Future.successful(Ok(views.html.admin.cases.owners(formWithErrors, request.`case`)))
          },
          data => {
            caseService.setOwners(request.`case`.id.get, data.toSet).successFlatMap { setOwnersResult =>
              notificationService.newCaseOwner(setOwnersResult.added.map(_.userId).toSet, request.`case`).successMap(_ =>
                Redirect(controllers.admin.routes.CaseController.view(request.`case`.key.get))
                  .flashing("success" -> Messages("flash.case.owners.updated"))
              )
            }
          }
        )
      )
    )
  }

  def caseSubmitSelf(caseKey: IssueKey): Action[AnyContent] = CanEditCaseAction(caseKey).async { implicit request =>
    caseService.getOwners(Set(request.`case`.id.get)).successFlatMap { ownerMap =>
      val currentOwners = ownerMap.getOrElse(request.`case`.id.get, Set())
      if (currentOwners.map(_.usercode).contains(currentUser.usercode)) {
        Future.successful(Redirect(controllers.admin.routes.AdminController.teamHome(request.`case`.team.id).withFragment("cases")))
      } else {
        caseService.setOwners(request.`case`.id.get, currentOwners.map(_.usercode) + currentUser.usercode).successMap { _ =>
          Redirect(controllers.admin.routes.CaseController.view(request.`case`.key.get))
            .flashing("success" -> Messages("flash.case.owners.updated"))
        }
      }
    }
  }

  /**
    * Check is each of the provided user codes in the request is a found user and in any team
    */
  private def bindAndVerifyOwners(existing: Set[Member], allowEmpty: Boolean)(implicit request: Request[_]): ServiceResult[Form[Seq[Usercode]]] = {
    ownersForm.bindFromRequest.fold(
      formWithErrors => Right(formWithErrors),
      usercodes => {
        if (usercodes.nonEmpty || allowEmpty) {
          val usercodesToValidate = usercodes.toSet.diff(existing.map(_.usercode)) // Only validate new usercodes
          val users = userLookupService.getUsers(usercodesToValidate.toSeq).toOption.getOrElse(Map.empty)
          val invalid = usercodesToValidate.filter(u => !users.get(u).exists(_.isFound))
          if (invalid.nonEmpty) {
            Right(
              ownersForm.fill(usercodes)
                .withError(FormError("owners", "error.userIds.invalid", Seq(invalid.map(_.string).mkString(", "))))
            )
          } else {
            permissionService.inAnyTeam(usercodesToValidate).fold(
              serviceErrors => Left(serviceErrors),
              resultMap => {
                val notInAnyTeam = resultMap.filter { case (_, inAnyTeam) => !inAnyTeam }.map { case (user, _) => user }.toSeq
                if (notInAnyTeam.isEmpty) {
                  Right(ownersForm.fill(usercodes))
                } else {
                  Right(
                    ownersForm.fill(usercodes)
                      .withError(FormError("owners", "error.owners.invalid", Seq(notInAnyTeam.map(_.string).mkString(", "))))
                  )
                }
              }
            )
          }
        } else {
          Right(ownersForm.fill(existing.map(_.usercode).toSeq).withGlobalError("error.owners.case.nonEmpty"))
        }
      }
    )
  }

}
