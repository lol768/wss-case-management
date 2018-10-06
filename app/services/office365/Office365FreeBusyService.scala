package services.office365

import java.time.LocalDate

import com.google.inject.ImplementedBy
import helpers.JavaTime
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.caching.{CacheElement, CacheOptions, Ttl, VariableTtlCacheHelper}
import javax.inject.{Inject, Singleton}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import services.FreeBusyService
import services.FreeBusyService.FreeBusyPeriod
import services.office365.Office365FreeBusyService.Office365FreeBusyServiceError
import system.{Logging, TimingCategories}
import warwick.core.timing.{TimingContext, TimingService}
import warwick.office365.O365Service
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[Office365FreeBusyServiceImpl])
trait Office365FreeBusyService extends FreeBusyService

object Office365FreeBusyService {
  case class Office365FreeBusyServiceError(message: String) extends ServiceError
}

@Singleton
class Office365FreeBusyServiceImpl @Inject()(
  office365: O365Service,
  cache: AsyncCacheApi,
  userLookupService: UserLookupService,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends Office365FreeBusyService with Logging {
  import timing._

  private val TimingCategory = TimingCategories.Office365

  private lazy val ttlStrategy: ServiceResult[Seq[FreeBusyPeriod]] => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 15.minutes, medium = 30.minutes, hard = 1.day)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[ServiceResult[Seq[FreeBusyPeriod]]](cache, logger, ttlStrategy, timing)

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] = time(TimingCategory) {
    wrappedCache.getOrElseUpdateElement(s"office365freebusy:${universityID.string}:${start.toString}-${end.toString}", CacheOptions.default) {
      userLookupService.getUsers(Seq(universityID)).toOption.flatMap(_.get(universityID)).map { user =>
        val queryParams: Seq[(String, String)] = Seq(
          "startDateTime" -> start.atStartOfDay(JavaTime.timeZone).format(JavaTime.iSO8601DateFormat),
          "endDateTime" -> end.plusDays(1).atStartOfDay(JavaTime.timeZone).format(JavaTime.iSO8601DateFormat),
          "$select" -> "Start,End,IsAllDay,ShowAs",
          "$top" -> "100"
        )

        office365.getO365(user.usercode.string, "CalendarView", queryParams)
          .map {
            case None => Right(Nil)
            case Some(json) =>
              Office365ResponseParsers.validateAPIResponse(json, Office365ResponseParsers.calendarEventsReads).fold(
                errors => handleValidationError(json, errors),
                events => Right(events.map { event =>
                  FreeBusyPeriod(
                    start = event.start.toOffsetDateTime,
                    end = event.end.toOffsetDateTime,
                    status = event.freeBusyStatus
                  )
                })
              )
          }
      }.getOrElse(Future.successful(Left(List(Office365FreeBusyServiceError(s"Couldn't find an ITS usercode for university ID ${universityID.string}")))))
    }
  }

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[Seq[FreeBusyPeriod]] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      Office365FreeBusyServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

}