package services.tabula

import java.security.MessageDigest

import play.api.Configuration
import warwick.sso.UniversityID

trait ProvidesPhotoUrl {

  def configuration: Configuration

  private val photosHost = configuration.get[String]("wellbeing.photos.host")
  private val photosAppName = configuration.get[String]("wellbeing.photos.appname")
  private val photosKey = configuration.get[String]("wellbeing.photos.key")

  protected def photoUrl(universityId: UniversityID): String = {
    val hash = MessageDigest.getInstance("MD5").digest(s"$photosKey${universityId.string}".getBytes)
      .map("%02x".format(_)).mkString

    s"https://$photosHost/$photosAppName/photo/$hash/${universityId.string}"
  }

}
