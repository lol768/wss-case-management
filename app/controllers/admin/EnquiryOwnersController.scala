package controllers.admin

import java.util.UUID

import controllers.BaseController
import controllers.refiners.CanEditEnquiryActionRefiner
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Forms.{mapping, seq, text}
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.EnquiryService
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryOwnersController @Inject()(
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  userLookupService: UserLookupService,
  enquiryService: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  val ownersForm = Form(mapping(
    "owners" -> seq(text)
  )(s => s)(s => Option(s)))

  import canEditEnquiryActionRefiner._

  def form(id: UUID): Action[AnyContent] = CanEditEnquiryAction(id).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id.get)).successMap(owners =>
      Ok(views.html.admin.enquiry.owners(
        ownersForm.fill(owners.getOrElse(request.enquiry.id.get, Set()).map(_.string).toSeq.sorted),
        request.enquiry
      ))
    )
  }

  def submit(id: UUID): Action[AnyContent] = CanEditEnquiryAction(id).async { implicit request =>
    ownersForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
      },
      data => {
        val userIds = data.filter(_.hasText).map(Usercode)
        val users = userLookupService.getUsers(userIds).toOption.getOrElse(Map.empty)
        val invalid = userIds.filter(u => !users.get(u).exists(_.isFound))
        if (invalid.nonEmpty) {
          val formWithErrors = ownersForm.fill(userIds.map(_.string))
            .withError(FormError("owners", "error.userId.invalid", invalid.map(_.string).mkString(", ")))
          Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
        } else {
          enquiryService.setOwners(request.enquiry.id.get, userIds.toSet).successMap(_ =>
            Redirect(controllers.admin.routes.AdminController.teamHome(request.enquiry.team.id))
              .flashing("success" -> Messages("flash.enquiry.owners.updated"))
          )
        }
      }
    )
  }

}
