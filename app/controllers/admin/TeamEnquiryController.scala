package controllers.admin

import java.util.UUID

import controllers.BaseController
import controllers.enquiries.EnquirySpecificActionRefiner
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.EnquiryService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamEnquiryController @Inject()(
  enquirySpecificActionRefiner: EnquirySpecificActionRefiner,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import enquirySpecificActionRefiner._

  private val reassignForm = Form(single(
    "team" -> Teams.formField
  ))

  def reassignForm(id: UUID): Action[AnyContent] = EnquirySpecificTeamMemberAction(id) { implicit request =>
    Ok(views.html.admin.enquiry.reassign(request.enquiry, reassignForm.fill(request.enquiry.team)))
  }

  def reassign(id: UUID): Action[AnyContent] = EnquirySpecificTeamMemberAction(id).async { implicit request =>
    reassignForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Ok(views.html.admin.enquiry.reassign(request.enquiry, formWithErrors))),
      team =>
        if (team == request.enquiry.team) // No change
          Future.successful(Redirect(controllers.admin.routes.AdminController.teamHome(team.id)))
        else
          service.reassign(request.enquiry, team).successMap { _ =>
            Redirect(controllers.admin.routes.AdminController.teamHome(request.enquiry.team.id))
              .flashing("success" -> Messages("flash.enquiry.reassigned", team.name))
          }
    )
  }

}
