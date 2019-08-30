package controllers.enquiries

import java.time.{DayOfWeek, LocalTime}

import controllers.enquiries.EnquiryController._
import controllers.refiners.ValidUniversityIDActionFilter
import controllers.BaseController
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import services.{EnquiryService, SecurityService}
import uk.ac.warwick.util.workingdays.{WorkingDaysHelper, WorkingDaysHelperImpl}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.{AuthenticatedRequest, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object EnquiryController {
  val consultationForm: Form[Boolean] = Form(single("submitted" -> default(boolean, true)))

  case class DisabilityEnquiryFormData(
    reasonableAdjustments: Boolean,
    specialistMentoring: Boolean,
    disabilityScreening: Boolean,
    generalAdvice: Boolean,
  ) {
    def enquiryText: String = views.txt.enquiry.disabilityQuery(this).toString.trim
  }
  val disabilityForm: Form[DisabilityEnquiryFormData] = Form(mapping(
    "reasonableAdjustments" -> boolean,
    "specialistMentoring" -> boolean,
    "disabilityScreening" -> boolean,
    "generalAdvice" -> boolean,
  )(DisabilityEnquiryFormData.apply)(DisabilityEnquiryFormData.unapply))

  val workingDaysHelper: WorkingDaysHelper = new WorkingDaysHelperImpl
  def outsideOfficeHours: Boolean = {
    val now = JavaTime.offsetDateTime

    if (workingDaysHelper.getHolidayDates.contains(now.toLocalDate)) true
    else {
      val time = now.toLocalTime

      now.getDayOfWeek match {
        case DayOfWeek.MONDAY | DayOfWeek.TUESDAY | DayOfWeek.WEDNESDAY | DayOfWeek.THURSDAY =>
          time.isBefore(LocalTime.of(8, 30)) || time.isAfter(LocalTime.of(17, 0))

        case DayOfWeek.FRIDAY =>
          time.isBefore(LocalTime.of(8, 30)) || time.isAfter(LocalTime.of(16, 0))

        case _ => true
      }
    }
  }
}

@Singleton
class EnquiryController @Inject()(
  validUniversityIDActionFilter: ValidUniversityIDActionFilter,
  securityService: SecurityService,
  service: EnquiryService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._
  import validUniversityIDActionFilter._

  private def render(
    consultation: Option[Form[Boolean]] = None,
    disability: Option[Form[DisabilityEnquiryFormData]] = None,
  )(implicit req: RequestHeader): Result =
    Ok(views.html.enquiry.form(
      consultation.getOrElse(consultationForm),
      disability.getOrElse(disabilityForm),
      uploadedFileControllerHelper.supportedMimeTypes,
      outsideOfficeHours
    ))

  def form(): Action[AnyContent] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser) { implicit request =>
    render()
  }

  def submitConsultation(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    consultationForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(consultation = Some(formWithErrors))),
      _ =>
        submitEnquiry(Teams.Consultation, clientSubject = "Brief consultation", message = domain.MessageSave(
          text = views.txt.enquiry.consultationAutoResponse().toString.trim,
          sender = MessageSender.Team,
          teamMember = Some(Usercode("system"))
        ))
    )
  }

  def submitDisability(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    disabilityForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(disability = Some(formWithErrors))),
      formData =>
        submitEnquiry(Teams.Disability, clientSubject = "Disability query", message = domain.MessageSave(
          text = formData.enquiryText,
          sender = MessageSender.Client,
          teamMember = None
        ))
    )
  }

  private def submitEnquiry(assigningTeam: Team, clientSubject: String, message: domain.MessageSave)(implicit request: AuthenticatedRequest[MultipartFormData[TemporaryUploadedFile]]): Future[Result] = {
    val enquiry = EnquirySave(
      universityID = currentUser().universityId.get,
      subject = clientSubject,
      team = assigningTeam,
      state = IssueState.Open
    )

    val files = request.body.files.map(_.ref)

    service.save(enquiry, message, files.map { f => (f.in, f.metadata) })
      .successMap { _ =>
        Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
      }
  }

}
