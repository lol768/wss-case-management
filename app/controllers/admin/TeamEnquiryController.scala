package controllers.admin

import java.time.OffsetDateTime

import controllers.BaseController
import controllers.admin.TeamEnquiryController._
import controllers.refiners.CanEditEnquiryActionRefiner
import domain._
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.EnquiryService

import scala.concurrent.{ExecutionContext, Future}

object TeamEnquiryController {
  case class ReassignEnquiryData(
    team: Team,
    version: OffsetDateTime
  )

  def form(enquiry: Enquiry) = Form(
    mapping(
      "team" -> Teams.formField,
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == enquiry.version)
    )(ReassignEnquiryData.apply)(ReassignEnquiryData.unapply)
  )
}

@Singleton
class TeamEnquiryController @Inject()(
  canEditEnquiryActionRefiner: CanEditEnquiryActionRefiner,
  service: EnquiryService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import canEditEnquiryActionRefiner._

  def reassignForm(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey) { implicit request =>
    Ok(views.html.admin.enquiry.reassign(request.enquiry, form(request.enquiry).fill(ReassignEnquiryData(request.enquiry.team, request.enquiry.version))))
  }

  def reassign(enquiryKey: IssueKey): Action[AnyContent] = CanEditEnquiryAction(enquiryKey).async { implicit request =>
    form(request.enquiry).bindFromRequest().fold(
      formWithErrors => Future.successful(
        // TODO submitted team is lost here
        Ok(views.html.admin.enquiry.reassign(request.enquiry, formWithErrors.fill(ReassignEnquiryData(request.enquiry.team, request.enquiry.version))))
      ),
      data =>
        if (data.team == request.enquiry.team) // No change
          Future.successful(Redirect(controllers.admin.routes.AdminController.teamHome(data.team.id)))
        else
          service.reassign(request.enquiry, data.team, data.version).successMap { _ =>
            Redirect(controllers.admin.routes.AdminController.teamHome(request.enquiry.team.id))
              .flashing("success" -> Messages("flash.enquiry.reassigned", data.team.name))
          }
    )
  }

}
