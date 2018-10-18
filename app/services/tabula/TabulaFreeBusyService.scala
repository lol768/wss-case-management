package services.tabula

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.google.inject.ImplementedBy
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.caching.{CacheElement, CacheOptions, Ttl, VariableTtlCacheHelper}
import helpers.{JavaTime, ServiceResults, TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import play.api.libs.ws.WSClient
import services.FreeBusyService
import services.FreeBusyService.{FreeBusyPeriod, FreeBusyStatus}
import services.tabula.TabulaFreeBusyService._
import system.TimingCategories
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import warwick.core.Logging
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TabulaFreeBusyServiceImpl])
trait TabulaFreeBusyService extends FreeBusyService

object TabulaFreeBusyService {
  val tabulaLocalDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

  case class TabulaFreeBusyServiceError(message: String) extends ServiceError
}

@Singleton
class TabulaFreeBusyServiceImpl @Inject()(
  ws: WSClient,
  trustedApplicationsManager: TrustedApplicationsManager,
  cache: AsyncCacheApi,
  configuration: Configuration,
  userLookupService: UserLookupService,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaFreeBusyService with Logging {
  import timing._

  private val TimingCategory = TimingCategories.Tabula

  private lazy val tabulaUsercode = configuration.get[String]("wellbeing.tabula.user")
  private lazy val tabulaTimetableUrl = configuration.get[String]("wellbeing.tabula.timetable")

  private lazy val ttlStrategy: ServiceResult[Seq[FreeBusyPeriod]] => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 4.hours, medium = 8.hours, hard = 1.day)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[ServiceResult[Seq[FreeBusyPeriod]]](cache, logger, ttlStrategy, timing)

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] = time(TimingCategory) {
    wrappedCache.getOrElseUpdateElement(s"tabulatimetable:${universityID.string}:${start.toString}-${end.toString}", CacheOptions.default) {
      val url = tabulaTimetableUrl.replace(":universityID", universityID.string)
      val request = ws.url(url).addQueryStringParameters(
        "start" -> start.format(tabulaLocalDateFormat),
        "end" -> end.format(tabulaLocalDateFormat)
      )

      val trustedHeaders = TrustedApplicationUtils.getRequestHeaders(
        trustedApplicationsManager.getCurrentApplication,
        tabulaUsercode,
        WSRequestUriBuilder.buildUri(request).toString
      ).asScala.map(h => h.getName -> h.getValue).toSeq

      val jsonResponse = request.addHttpHeaders(trustedHeaders: _*).get()
        .map(r => ServiceResults.catchAsServiceError(Some("Trusted apps integration error")) {
          if (r.status == 404) None
          else Some(TrustedAppsHelper.validateResponse(url, r).json)
        })

      jsonResponse.map(response => {
        response.flatMap {
          case None => Right(Nil)
          case Some(json) =>
            TabulaResponseParsers.validateAPIResponse(json, TabulaResponseParsers.timetableEventsReads).fold(
              errors => handleValidationError(json, errors),
              events => Right(events.map { event =>
                FreeBusyPeriod(
                  start = event.start.atZone(JavaTime.timeZone).toOffsetDateTime,
                  end = event.end.atZone(JavaTime.timeZone).toOffsetDateTime,
                  status = FreeBusyStatus.Busy
                )
              })
            )
        }
      })
    }
  }

  override def findFreeBusyPeriods(usercode: Usercode, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    userLookupService.getUser(usercode).toOption.flatMap(_.universityId)
      .map(findFreeBusyPeriods(_, start, end))
      .getOrElse(Future.successful {
        val now = JavaTime.instant
        // Soft fail
        CacheElement(Right(Nil), now.getEpochSecond, now.getEpochSecond, now.getEpochSecond)
      })

  override def findFreeBusyPeriods(roomID: UUID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    Future.successful {
      val value = Right(Nil)
      val ttl = ttlStrategy(value)
      val now = JavaTime.instant
      val softExpiry = now.plusSeconds(ttl.soft.toSeconds).getEpochSecond
      val mediumExpiry = now.plusSeconds(ttl.medium.toSeconds).getEpochSecond
      CacheElement(value, now.getEpochSecond, softExpiry, mediumExpiry)
    }

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[Seq[FreeBusyPeriod]] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      TabulaFreeBusyServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

}