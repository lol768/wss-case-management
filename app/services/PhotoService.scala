package services

import java.security.MessageDigest

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import warwick.sso.UniversityID

@ImplementedBy(classOf[PhotoServiceImpl])
trait PhotoService {

  def photoUrl(universityId: UniversityID): String

}

@Singleton
class PhotoServiceImpl @Inject()(
  configuration: Configuration
) extends PhotoService {

  private lazy val photosHost = configuration.get[String]("wellbeing.photos.host")
  private lazy val photosAppName = configuration.get[String]("wellbeing.photos.appname")
  private lazy val photosKey = configuration.get[String]("wellbeing.photos.key")

  override def photoUrl(universityId: UniversityID): String = {
    val hash = MessageDigest.getInstance("MD5").digest(s"$photosKey${universityId.string}".getBytes)
      .map("%02x".format(_)).mkString

    s"https://$photosHost/$photosAppName/photo/$hash/${universityId.string}"
  }

}
