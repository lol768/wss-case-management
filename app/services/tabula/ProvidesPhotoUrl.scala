package services.tabula

import java.security.MessageDigest

import javax.inject.Inject
import play.api.Configuration
import warwick.sso.UniversityID

trait ProvidesPhotoUrl {

  @Inject
  protected var configuration: Configuration = _

  private lazy val photosHost = configuration.get[String]("wellbeing.photos.host")
  private lazy val photosAppName = configuration.get[String]("wellbeing.photos.appname")
  private lazy val photosKey = configuration.get[String]("wellbeing.photos.key")

  protected def photoUrl(universityId: UniversityID): String = {
    val hash = MessageDigest.getInstance("MD5").digest(s"$photosKey${universityId.string}".getBytes)
      .map("%02x".format(_)).mkString

    s"https://$photosHost/$photosAppName/photo/$hash/${universityId.string}"
  }

}
