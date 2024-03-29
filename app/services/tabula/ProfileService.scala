package services.tabula

import com.google.inject.ImplementedBy
import domain.SitsProfile
import helpers.{TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import play.api.libs.ws.WSClient
import services.PhotoService
import services.tabula.ProfileService._
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import warwick.caching.{CacheElement, CacheOptions, Ttl, VariableTtlCacheHelper}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.core.timing.{TimingCategories, TimingContext, TimingService}
import warwick.sso.UniversityID

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ProfileServiceImpl])
trait ProfileService {
  def getProfile(universityID: UniversityID)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Option[SitsProfile]]]]
  def getProfiles(universityIDs: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, SitsProfile]]]
}

object ProfileService {
  case class ProfileServiceError(message: String) extends ServiceError
}

@Singleton
class ProfileServiceImpl @Inject()(
  ws: WSClient,
  trustedApplicationsManager: TrustedApplicationsManager,
  cache: AsyncCacheApi,
  photoService: PhotoService,
  configuration: Configuration,
  timing: TimingService
)(implicit ec: ExecutionContext) extends ProfileService with Logging {
  import timing._

  private val TimingCategory = TimingCategories.Tabula

  private lazy val tabulaUsercode = configuration.get[String]("wellbeing.tabula.user")
  private lazy val tabulaProfileUrl = configuration.get[String]("wellbeing.tabula.profile")

  private lazy val ttlStrategy: ServiceResult[Option[SitsProfile]] => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.hour, medium = 1.day, hard = 7.days)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[ServiceResult[Option[SitsProfile]]](cache, logger, ttlStrategy, timing)

  override def getProfile(universityID: UniversityID)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Option[SitsProfile]]]] = time(TimingCategory) {
    wrappedCache.getOrElseUpdateElement(s"tabulaprofile:${universityID.string}", CacheOptions.default) {
      val url = s"$tabulaProfileUrl/${universityID.string}"
      val request = ws.url(url).addQueryStringParameters(
        "fields" -> TabulaResponseParsers.TabulaProfileData.memberFields.mkString(",")
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
          case None => Right(None)
          case Some(json) =>
            TabulaResponseParsers.validateAPIResponse(json, TabulaResponseParsers.TabulaProfileData.memberReads).fold(
              errors => handleValidationError(json, errors),
              data => {
                val tabulaProfile = data.toUserProfile
                Right(Some(tabulaProfile.copy(
                  photo = Some(photoService.photoUrl(universityID)),
                )))
              }
            )
        }
      })
    }
  }

  override def getProfiles(universityIDs: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, SitsProfile]]] = time(TimingCategory) {
    val profiles = ServiceResults.futureSequence(universityIDs.toSeq.map(id => getProfile(id).map(_.value)))
    profiles.map(_.map(_.flatten.map(p => (p.universityID, p)).toMap))
  }

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[Option[SitsProfile]] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      ProfileServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

}
