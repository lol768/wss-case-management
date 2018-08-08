package helpers.caching

import java.time.{Instant, ZonedDateTime}
import java.util.UUID

import helpers.JavaTime
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, AnyContentAsJson, AnyContentAsText, Request}

import scala.collection.Iterable

case class CacheInformation(key: String, public: Boolean, date: ZonedDateTime, expires: ZonedDateTime) {
  def etag(implicit request: Request[AnyContent]): String = {
    val md5 = java.security.MessageDigest
      .getInstance("MD5")
      .digest(
        Seq(
          key,
          expires.toInstant.getEpochSecond.toString,
          if (request.hasBody) request.body match {
            case AnyContentAsText(txt) => txt
            case AnyContentAsJson(js) => Json.stringify(js)
            case b => b.toString
          } else ""
        ).mkString(";").getBytes
      )
      .map(0xFF & _)
      .map {
        "%02x".format(_)
      }
      .foldLeft("") {
        _ + _
      }

    s"""W/"$md5""""
  }
}

object CacheInformation {
  def apply(key: String, public: Boolean, element: CacheElement[_]): CacheInformation =
    CacheInformation(
      key = key,
      public = public,
      date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(element.created), JavaTime.timeZone),
      expires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(element.expires), JavaTime.timeZone)
    )

  def public(key: String, element: CacheElement[_]): CacheInformation =
    CacheInformation(key, public = true, element)

  def `private`(key: String, element: CacheElement[_]): CacheInformation =
    CacheInformation(key, public = false, element)

  def merge(info: CacheInformation*): CacheInformation =
    merge(info)

  def merge(info: Iterable[CacheInformation]): CacheInformation =
    if (info.isEmpty) noCache
    else CacheInformation(
      key = info.map(_.key).toSeq.distinct.sorted.mkString(";"),
      public = info.forall(_.public),
      date = info.map(_.date).max,
      expires = info.map(_.expires).min
    )

  def noCache: CacheInformation =
    CacheInformation(
      key = UUID.randomUUID().toString,
      public = false,
      date = JavaTime.zonedDateTime,
      expires = JavaTime.zonedDateTime.minusSeconds(1)
    )
}