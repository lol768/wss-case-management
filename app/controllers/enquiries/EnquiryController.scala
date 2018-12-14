package controllers.enquiries

import java.time.{DayOfWeek, LocalTime}

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.enquiries.EnquiryController._
import controllers.refiners.ValidUniversityIDActionFilter
import controllers.{BaseController, UploadedFileControllerHelper}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import services.{EnquiryService, SecurityService}
import uk.ac.warwick.util.workingdays.{WorkingDaysHelper, WorkingDaysHelperImpl}
import warwick.core.helpers.JavaTime
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

object EnquiryController {
  case class MentalHealthEnquiryFormData(
    reasonableAdjustments: Boolean,
    studySkills: Boolean,
    generalAdvice: Boolean,
  ) {
    def enquiryText: String = views.txt.enquiry.mentalHealthQuery(this).toString.trim
  }
  val mentalHealthForm: Form[MentalHealthEnquiryFormData] = Form(mapping(
    "reasonableAdjustments" -> boolean,
    "studySkills" -> boolean,
    "generalAdvice" -> boolean,
  )(MentalHealthEnquiryFormData.apply)(MentalHealthEnquiryFormData.unapply))

  case class CounsellingEnquiryFormData(
    faceToFace: Boolean,
    therapy: Boolean,
    online: Boolean,
  ) {
    def enquiryText: String = views.txt.enquiry.counsellingQuery(this).toString.trim
  }
  val counsellingForm: Form[CounsellingEnquiryFormData] = Form(mapping(
    "faceToFace" -> boolean,
    "therapy" -> boolean,
    "online" -> boolean,
  )(CounsellingEnquiryFormData.apply)(CounsellingEnquiryFormData.unapply))

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

  val wellbeingForm: Form[String] = Form(single("text" -> nonEmptyText))

  val workingDaysHelper: WorkingDaysHelper = new WorkingDaysHelperImpl
  def outsideOfficeHours: Boolean = {
    val now = JavaTime.offsetDateTime

    if (workingDaysHelper.getHolidayDates.contains(now.toLocalDate)) true
    else {
      val time = now.toLocalTime

      now.getDayOfWeek match {
        case DayOfWeek.MONDAY | DayOfWeek.TUESDAY | DayOfWeek.WEDNESDAY | DayOfWeek.THURSDAY =>
          time.isBefore(LocalTime.of(9, 0)) || time.isAfter(LocalTime.of(17, 0))

        case DayOfWeek.FRIDAY =>
          time.isBefore(LocalTime.of(9, 0)) || time.isAfter(LocalTime.of(16, 0))

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
    mentalHealth: Option[Form[MentalHealthEnquiryFormData]] = None,
    counselling: Option[Form[CounsellingEnquiryFormData]] = None,
    disability: Option[Form[DisabilityEnquiryFormData]] = None,
    wellbeing: Option[Form[String]] = None,
  )(implicit req: RequestHeader): Result =
    Ok(views.html.enquiry.form(
      mentalHealth.getOrElse(mentalHealthForm),
      counselling.getOrElse(counsellingForm),
      disability.getOrElse(disabilityForm),
      wellbeing.getOrElse(wellbeingForm),
      uploadedFileControllerHelper.supportedMimeTypes,
      outsideOfficeHours
    ))

  def form(): Action[AnyContent] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser) { implicit request =>
    render()
  }

  def submitMentalHealth(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    mentalHealthForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(mentalHealth = Some(formWithErrors))),
      formData =>
        submitEnquiry(Teams.MentalHealth, clientSubject = "Mental health query", enquiryText = formData.enquiryText)
    )
  }

  def submitCounselling(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    counsellingForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(counselling = Some(formWithErrors))),
      formData =>
        submitEnquiry(Teams.Counselling, clientSubject = "Counselling query", enquiryText = formData.enquiryText)
    )
  }

  def submitDisability(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    disabilityForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(disability = Some(formWithErrors))),
      formData =>
        submitEnquiry(Teams.Disability, clientSubject = "Disability query", enquiryText = formData.enquiryText)
    )
  }

  def submitWellbeing(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    wellbeingForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(wellbeing = Some(formWithErrors))),
      enquiryText =>
        submitEnquiry(Teams.WellbeingSupport, clientSubject = "Wellbeing query", enquiryText = enquiryText)
    )
  }

  private def submitEnquiry(assigningTeam: Team, clientSubject: String, enquiryText: String)(implicit request: AuthenticatedRequest[MultipartFormData[TemporaryUploadedFile]]): Future[Result] = {
    val enquiry = EnquirySave(
      universityID = currentUser().universityId.get,
      subject = clientSubject,
      team = assigningTeam,
      state = IssueState.Open
    )

    val message = domain.MessageSave(
      text = enquiryText,
      sender = MessageSender.Client,
      teamMember = None
    )

    val files = request.body.files.map(_.ref)

    service.save(enquiry, message, files.map { f => (f.in, f.metadata) })
      .successMap { _ =>
        Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
      }
  }

}
