package services.tabula

import java.security.MessageDigest
import com.google.inject.ImplementedBy
import domain.UserProfile
import helpers.{TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.{JsPath, JsonValidationError}
import play.api.libs.ws.WSClient
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import warwick.sso.UniversityID

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._
import system.Logging

import helpers.caching.VariableTtlCacheHelper
import play.api.cache.AsyncCacheApi


@ImplementedBy(classOf[ProfileServiceImpl])
trait ProfileService {
  def getProfile(universityID: UniversityID): Future[Option[UserProfile]]
}

class ProfileServiceImpl  @Inject()(
  ws: WSClient,
  trustedApplicationsManager: TrustedApplicationsManager,
  configuration: Configuration,
  cache: AsyncCacheApi
) extends ProfileService with Logging {

  private val tabulaUsercode = configuration.get[String]("wellbeing.tabula.user")
  private val tabulaProfileUrl = configuration.get[String]("wellbeing.tabula.profile")

  private val photosHost = configuration.get[String]("wellbeing.photos.host")
  private val photosAppName = configuration.get[String]("wellbeing.photos.appname")
  private val photosKey = configuration.get[String]("wellbeing.photos.key")

  private lazy val wrappedCache =
    VariableTtlCacheHelper.async[Option[UserProfile]](cache, logger, 1.hour, 1.day, 7.days)


  override def getProfile(universityID: UniversityID): Future[Option[UserProfile]] = wrappedCache.getOrElseUpdate(universityID.string){

    val url = s"$tabulaProfileUrl/${universityID.string}"
    val request = ws.url(url)

    val trustedHeaders = TrustedApplicationUtils.getRequestHeaders(
      trustedApplicationsManager.getCurrentApplication,
      tabulaUsercode,
      WSRequestUriBuilder.buildUri(request).toString
    ).asScala.map(h => h.getName -> h.getValue).toSeq

    val jsonResponse = request.addHttpHeaders(trustedHeaders: _*).get()
      .map(r => TrustedAppsHelper.validateResponse(url, r))
      .map(_.json)

    jsonResponse.map{
      TabulaResponseParsers.validateAPIResponse(_, TabulaResponseParsers.TabulaProfileData.memberReads).fold(
        handleValidationError(_, None),
        data => {
          val tabulaProfile = data.toUserProfile
          Some(tabulaProfile.copy(photo = Some(photoUrl(universityID))))
        }
      )
    }

  }

  private def handleValidationError[A](errors: Seq[(JsPath, Seq[JsonValidationError])], fallback: A): A = {
    logger.error(s"Could not parse JSON result from Tabula:")
    errors.foreach { case (path, validationErrors) =>
      logger.error(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    fallback
  }

  private def photoUrl(universityId: UniversityID): String = {
    val hash = MessageDigest.getInstance("MD5").digest(s"$photosKey${universityId.string}".getBytes)
      .map("%02x".format(_)).mkString

    s"https://$photosHost/$photosAppName/photo/$hash/${universityId.string}"
  }

}
