package services.office365

import java.time.LocalDate
import java.util.UUID

import com.google.inject.ImplementedBy
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.ServiceResults.Implicits._
import helpers.caching.{CacheElement, CacheOptions, Ttl, VariableTtlCacheHelper}
import javax.inject.{Inject, Singleton}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import services.{FreeBusyService, LocationService}
import services.FreeBusyService.{FreeBusyPeriod, FreeBusyStatus}
import services.office365.Office365FreeBusyService.Office365FreeBusyServiceError
import system.TimingCategories
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.{TimingContext, TimingService}
import warwick.office365.O365Service
import warwick.sso.{UniversityID, UserLookupService, Usercode}

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
  locations: LocationService,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends Office365FreeBusyService with Logging {
  import timing._

  private val TimingCategory = TimingCategories.Office365

  private lazy val ttlStrategy: ServiceResult[Seq[FreeBusyPeriod]] => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 15.minutes, medium = 30.minutes, hard = 1.day)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[ServiceResult[Seq[FreeBusyPeriod]]](cache, logger, ttlStrategy, timing)

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] =
    userLookupService.getUsers(Seq(universityID)).toOption.flatMap(_.get(universityID))
      .map { user =>
        findFreeBusyPeriods(user.usercode, start, end)
      }
      .getOrElse(Future.successful {
        val now = JavaTime.instant
        // Soft fail
        CacheElement(Right(Nil), now.getEpochSecond, now.getEpochSecond, now.getEpochSecond)
      })

  override def findFreeBusyPeriods(usercode: Usercode, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] = time(TimingCategory) {
    wrappedCache.getOrElseUpdateElement(s"office365freebusy:${usercode.string}:${start.toString}-${end.toString}", CacheOptions.default) {
      val queryParams: Seq[(String, String)] = Seq(
        "startDateTime" -> start.atStartOfDay(JavaTime.timeZone).format(JavaTime.iSO8601DateFormat),
        "endDateTime" -> end.plusDays(1).atStartOfDay(JavaTime.timeZone).format(JavaTime.iSO8601DateFormat),
        "$select" -> "Start,End,IsAllDay,ShowAs,Categories",
        "$top" -> "100"
      )

      office365.getO365(usercode.string, "CalendarView", queryParams)
        .map {
          case None => Right(Nil)
          case Some(json) =>
            Office365ResponseParsers.validateAPIResponse(json, Office365ResponseParsers.calendarEventsReads).fold(
              errors => handleValidationError(json, errors),
              events => Right(events.map { event =>
                FreeBusyPeriod(
                  start = event.start.toOffsetDateTime,
                  end = event.end.toOffsetDateTime,
                  status =
                    if (event.freeBusyStatus == FreeBusyStatus.Free && event.categories.nonEmpty)
                      FreeBusyStatus.FreeWithCategories(event.categories)
                    else event.freeBusyStatus
                )
              })
            )
        }
    }
  }

  override def findFreeBusyPeriods(roomID: UUID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    locations.findRoom(roomID).flatMap {
      case Left(errors) =>
        Future.successful {
          val now = JavaTime.instant
          CacheElement(Left(errors), now.getEpochSecond, now.getEpochSecond, now.getEpochSecond)
        }

      case Right(room) if room.o365Usercode.isEmpty =>
        Future.successful {
          val now = JavaTime.instant
          CacheElement(Right(Nil), now.getEpochSecond, now.getEpochSecond, now.getEpochSecond)
        }

      case Right(room) =>
        findFreeBusyPeriods(room.o365Usercode.get, start, end)
    }

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[Seq[FreeBusyPeriod]] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      Office365FreeBusyServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

}