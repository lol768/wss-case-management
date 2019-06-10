package controllers

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime, ZoneId}

import helpers.Json._
import org.threeten.extra.Interval
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.{Formats, Formatter}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationResult}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Result, Results}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.sso.{AuthenticatedRequest, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ControllerHelper extends Results with Logging {
  self: BaseController =>

  def currentUser()(implicit request: AuthenticatedRequest[_]): User = request.context.user.get

  def showErrors(errors: Seq[_ <: ServiceError])(implicit request: RequestHeader): Result =
    render {
      case Accepts.Json() => BadRequest(Json.toJson(JsonClientError(status = "bad_request", errors = errors.map(_.message))))
      case _ => BadRequest(views.html.errors.multiple(errors))
    }

  implicit class FutureServiceResultControllerOps[A](val future: Future[ServiceResult[A]]) {
    def successMap(fn: A => Result)(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.map { result =>
        result.fold(showErrors, fn)
      }

    def successFlatMap(fn: A => Future[Result])(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.flatMap { result =>
        result.fold(
          e => Future.successful(showErrors(e)),
          fn
        )
      }
  }

  implicit def futureServiceResultOps[A](future: Future[ServiceResult[A]]): FutureServiceResultOps[A] =
    new FutureServiceResultOps[A](future)
}

case class DateRange(start: LocalDate, end: LocalDate) {
  val startTime: OffsetDateTime = start.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime
  val endTime: OffsetDateTime = end.atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1).toOffsetDateTime
  val interval: Interval = Interval.of(startTime.toInstant, endTime.toInstant)
  val isViable: Boolean = interval.toDuration.toDays <= DateRange.maxRange

  override def toString: String = s"${start.format(DateRange.localDateStringFormat)} to ${end.format(DateRange.localDateStringFormat)} inclusive"
}

object DateRange {
  implicit object LocalDateFormatter extends Formatter[LocalDate] {
    override val format = Some(("format.localdate", Nil))
    override def bind(key: String, data: Map[String, String]) = Formats.parsing(LocalDate.parse(_, localDatePickerFormat), "error.localDate", Nil)(key, data)
    override def unbind(key: String, value: LocalDate) = Map(key -> value.format(localDatePickerFormat))
  }

  val localDatePickerFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val localDateStringFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
  val epoch = LocalDate.ofEpochDay(0)
  val maxRange = 366

  val validationConstraint = Constraint[DateRange] ({
    case dates if dates.start.isAfter(dates.end) => Invalid("The end date must be after the start date")
    case dates if !dates.isViable => Invalid(s"Date range must be less than ${maxRange} days")
    case _ => Valid
  }: PartialFunction[DateRange, ValidationResult])

  def form = Form(
    mapping[DateRange, LocalDate, LocalDate](
      "start" -> default(of[LocalDate], epoch),
      "end" -> default(of[LocalDate], LocalDate.now)
    )(DateRange.apply)(DateRange.unapply)
      verifying(validationConstraint)
  )
  
  def apply(start: OffsetDateTime, end: OffsetDateTime): DateRange = DateRange(start.toLocalDate, end.toLocalDate)
}
