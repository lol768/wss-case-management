package controllers.admin

import java.util.UUID

import controllers.BaseController
import controllers.enquiries.EnquirySpecificActionRefiner
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Forms.{mapping, seq, text}
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.EnquiryService
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryOwnersController @Inject()(
  enquirySpecificActionRefiner: EnquirySpecificActionRefiner,
  enquiryService: EnquiryService,
  userLookupService: UserLookupService
)(implicit executionContext: ExecutionContext) extends BaseController {

  val ownersForm = Form(mapping(
    "owners" -> seq(text)
  )(s => s)(s => Option(s)))

  import enquirySpecificActionRefiner._

  def form(id: UUID): Action[AnyContent] = EnquirySpecificTeamMemberAction(id).async { implicit request =>
    enquiryService.getOwners(Set(request.enquiry.id.get)).successMap(owners =>
      Ok(views.html.admin.enquiry.owners(
        ownersForm.fill(owners.getOrElse(request.enquiry.id.get, Set()).map(_.universityID.string).toSeq.sorted),
        request.enquiry
      ))
    )
  }

  def submit(id: UUID): Action[AnyContent] = EnquirySpecificTeamMemberAction(id).async { implicit request =>
    ownersForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
      },
      data => {
        val universityIDs = data.filter(_.hasText).map(UniversityID)
        val users = userLookupService.getUsers(universityIDs).toOption.getOrElse(Map.empty)
        val invalid = universityIDs.filter(u => !users.get(u).exists(_.isFound))
        if (invalid.nonEmpty) {
          val formWithErrors = ownersForm.fill(universityIDs.map(_.string))
            .withError(FormError("admins", "error.universityIDs.invalid", invalid.map(_.string).mkString(", ")))
          Future.successful(Ok(views.html.admin.enquiry.owners(formWithErrors, request.enquiry)))
        } else {
          enquiryService.setOwners(request.enquiry.id.get, universityIDs).successMap(_ =>
            Redirect(controllers.admin.routes.AdminController.teamHome(request.enquiry.team.id))
              .flashing("success" -> Messages("flash.enquiry.owners.updated"))
          )
        }
      }
    )
  }

}
