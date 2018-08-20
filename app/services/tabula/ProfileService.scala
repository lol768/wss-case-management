package services.tabula

import java.security.MessageDigest

import com.google.inject.ImplementedBy
import domain.SitsProfile
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.{ServiceResults, TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import play.api.libs.ws.WSClient
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import warwick.sso.UniversityID

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import system.Logging
import helpers.caching.{CacheElement, CacheOptions, Ttl, VariableTtlCacheHelper}
import play.api.cache.AsyncCacheApi


@ImplementedBy(classOf[ProfileServiceImpl])
trait ProfileService {
  def getProfile(universityID: UniversityID): Future[CacheElement[ServiceResult[SitsProfile]]]
}

class ProfileServiceImpl  @Inject()(
  ws: WSClient,
  trustedApplicationsManager: TrustedApplicationsManager,
  configuration: Configuration,
  cache: AsyncCacheApi
)(implicit ec: ExecutionContext) extends ProfileService with Logging {

  case class ProfileServiceError(message: String) extends ServiceError

  private val tabulaUsercode = configuration.get[String]("wellbeing.tabula.user")
  private val tabulaProfileUrl = configuration.get[String]("wellbeing.tabula.profile")

  private val photosHost = configuration.get[String]("wellbeing.photos.host")
  private val photosAppName = configuration.get[String]("wellbeing.photos.appname")
  private val photosKey = configuration.get[String]("wellbeing.photos.key")

  private lazy val ttlStrategy: ServiceResult[SitsProfile] => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.hour, medium = 1.day, hard = 7.days)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[ServiceResult[SitsProfile]](cache, logger, ttlStrategy)


  override def getProfile(universityID: UniversityID): Future[CacheElement[ServiceResult[SitsProfile]]] =
    wrappedCache.getOrElseUpdateElement(s"tabulaprofile:${universityID.string}"){
      val url = s"$tabulaProfileUrl/${universityID.string}"
      val request = ws.url(url)

      val trustedHeaders = TrustedApplicationUtils.getRequestHeaders(
        trustedApplicationsManager.getCurrentApplication,
        tabulaUsercode,
        WSRequestUriBuilder.buildUri(request).toString
      ).asScala.map(h => h.getName -> h.getValue).toSeq

      val jsonResponse = request.addHttpHeaders(trustedHeaders: _*).get()
        .map(r => ServiceResults.throwableToError(Some("Trusted apps integration error")) {
          TrustedAppsHelper.validateResponse(url, r).json
        })

      jsonResponse.map(response => {
        response.flatMap(json => {
          TabulaResponseParsers.validateAPIResponse(json, TabulaResponseParsers.TabulaProfileData.memberReads).fold(
            errors => handleValidationError(json, errors),
            data => {
              val tabulaProfile = data.toUserProfile
              Right(tabulaProfile.copy(photo = Some(photoUrl(universityID))))
            }
          )
        })
      })
    }(CacheOptions.default)

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[SitsProfile] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      ProfileServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

  private def photoUrl(universityId: UniversityID): String = {
    val hash = MessageDigest.getInstance("MD5").digest(s"$photosKey${universityId.string}".getBytes)
      .map("%02x".format(_)).mkString

    s"https://$photosHost/$photosAppName/photo/$hash/${universityId.string}"
  }

}
