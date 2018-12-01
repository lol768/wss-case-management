package controllers.enquiries

import java.time.{DayOfWeek, LocalTime}

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.enquiries.EnquiryController._
import controllers.refiners.ValidUniversityIDActionFilter
import controllers.{BaseController, UploadedFileControllerHelper}
import domain._
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import services.{EnquiryService, SecurityService}
import warwick.core.helpers.JavaTime

import scala.concurrent.{ExecutionContext, Future}

object EnquiryController {
  case class MentalHealthEnquiryFormData(
    reasonableAdjustments: Option[Boolean],
    studySkills: Option[Boolean],
    generalAdvice: Option[Boolean],
  ) {
    val isEmpty: Boolean = Seq(reasonableAdjustments, studySkills, generalAdvice).exists(_.contains(true))
    val nonEmpty: Boolean = !isEmpty

    def enquiryText: String = views.txt.enquiry.mentalHealthQuery(this).toString.trim
  }

  case class CounsellingEnquiryFormData(
    faceToFace: Option[Boolean],
    therapy: Option[Boolean],
    online: Option[Boolean],
  ) {
    val isEmpty: Boolean = Seq(faceToFace, therapy, online).exists(_.contains(true))
    val nonEmpty: Boolean = !isEmpty

    def enquiryText: String = views.txt.enquiry.counsellingQuery(this).toString.trim
  }

  case class DisabilityEnquiryFormData(
    reasonableAdjustments: Option[Boolean],
    specialistMentoring: Option[Boolean],
    disabilityScreening: Option[Boolean],
    generalAdvice: Option[Boolean],
  ) {
    val isEmpty: Boolean = Seq(reasonableAdjustments, specialistMentoring, disabilityScreening, generalAdvice).exists(_.contains(true))
    val nonEmpty: Boolean = !isEmpty

    def enquiryText: String = views.txt.enquiry.disabilityQuery(this).toString.trim
  }

  case class EnquiryFormData(
    mentalHealth: Option[Boolean],
    mentalHealthFields: Option[MentalHealthEnquiryFormData],
    counselling: Option[Boolean],
    counsellingFields: Option[CounsellingEnquiryFormData],
    disability: Option[Boolean],
    disabilityFields: Option[DisabilityEnquiryFormData],
    wellbeingText: Option[String],
  ) {
    val assigningTeam: Team =
      if (!mentalHealth.contains(false) && mentalHealthFields.exists(_.nonEmpty)) Teams.MentalHealth
      else if (!counselling.contains(false) && counsellingFields.exists(_.nonEmpty)) Teams.Counselling
      else if (!disability.contains(false) && disabilityFields.exists(_.nonEmpty)) Teams.Disability
      else Teams.WellbeingSupport

    val clientSubject: String = assigningTeam match {
      case Teams.MentalHealth => "Mental health query"
      case Teams.Counselling => "Counselling query"
      case Teams.Disability => "Disability query"
      case Teams.WellbeingSupport => "Wellbeing query"
    }

    def enquiryText: String = assigningTeam match {
      case Teams.MentalHealth => mentalHealthFields.get.enquiryText
      case Teams.Counselling => counsellingFields.get.enquiryText
      case Teams.Disability => disabilityFields.get.enquiryText
      case Teams.WellbeingSupport => wellbeingText.get
    }
  }

  val enquiryForm: Form[EnquiryFormData] = Form(
    mapping(
      "mentalHealth" -> optional(boolean),
      "mentalHealthFields" -> optional(mapping(
        "reasonableAdjustments" -> optional(boolean),
        "studySkills" -> optional(boolean),
        "generalAdvice" -> optional(boolean),
      )(MentalHealthEnquiryFormData.apply)(MentalHealthEnquiryFormData.unapply)),
      "counselling" -> optional(boolean),
      "counsellingFields" -> optional(mapping(
        "faceToFace" -> optional(boolean),
        "therapy" -> optional(boolean),
        "online" -> optional(boolean),
      )(CounsellingEnquiryFormData.apply)(CounsellingEnquiryFormData.unapply)),
      "disability" -> optional(boolean),
      "disabilityFields" -> optional(mapping(
        "reasonableAdjustments" -> optional(boolean),
        "specialistMentoring" -> optional(boolean),
        "disabilityScreening" -> optional(boolean),
        "generalAdvice" -> optional(boolean),
      )(DisabilityEnquiryFormData.apply)(DisabilityEnquiryFormData.unapply)),
      "wellbeingText" -> optional(nonEmptyText),
    )(EnquiryFormData.apply)(EnquiryFormData.unapply)
      .verifying("error.enquiry.message.required", data => data.assigningTeam != Teams.WellbeingSupport || data.wellbeingText.exists(_.hasText))
      .verifying("error.enquiry.incomplete", data => data.assigningTeam match {
        case Teams.MentalHealth => data.mentalHealthFields.exists(_.nonEmpty)
        case Teams.Counselling => data.counsellingFields.exists(_.nonEmpty)
        case Teams.Disability => data.disabilityFields.exists(_.nonEmpty)
        case Teams.WellbeingSupport => true
      })
  )

  def outsideOfficeHours: Boolean = {
    val now = JavaTime.offsetDateTime
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

@Singleton
class EnquiryController @Inject()(
  validUniversityIDActionFilter: ValidUniversityIDActionFilter,
  securityService: SecurityService,
  service: EnquiryService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._
  import validUniversityIDActionFilter._

  private def render(f: Form[EnquiryFormData])(implicit req: RequestHeader): Result =
    Ok(views.html.enquiry.form(f, uploadedFileControllerHelper.supportedMimeTypes, outsideOfficeHours))

  def form(): Action[AnyContent] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser) { implicit request =>
    render(enquiryForm.bindFromRequest().discardingErrors)
  }

  def submit(): Action[MultipartFormData[TemporaryUploadedFile]] = SigninRequiredAction.andThen(ValidUniversityIDRequiredCurrentUser)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    enquiryForm.bindFromRequest().fold(
      formWithErrors => Future.successful(render(formWithErrors)),
      formData => {
        val enquiry = EnquirySave(
          universityID = currentUser().universityId.get,
          subject = formData.clientSubject,
          team = formData.assigningTeam,
          state = IssueState.Open
        )

        val message = domain.MessageSave(
          text = formData.enquiryText,
          sender = MessageSender.Client,
          teamMember = None
        )

        val files = request.body.files.map(_.ref)

        service.save(enquiry, message, files.map { f => (f.in, f.metadata) }).successMap { _ =>
          Redirect(controllers.routes.IndexController.home()).flashing("success" -> Messages("flash.enquiry.received"))
        }
      }
    )
  }

}
