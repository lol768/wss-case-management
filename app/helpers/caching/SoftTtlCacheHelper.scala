package helpers.caching

import helpers.JavaTime
import play.api.Logger
import play.api.cache.AsyncCacheApi

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object SoftTtlCacheHelper {
  def async[A: ClassTag](cache: AsyncCacheApi, logger: Logger, softTtl: FiniteDuration, mediumTtl: FiniteDuration, hardTtl: Duration)(implicit executor: ExecutionContext): AsyncSoftTtlCacheHelper[A] = {
    val ttl = Ttl(softTtl, mediumTtl, hardTtl)
    new AsyncSoftTtlCacheHelper[A](cache, logger, _ => ttl)
  }

  def async[A: ClassTag](cache: AsyncCacheApi, logger: Logger, ttl: A => Ttl)(implicit executor: ExecutionContext): AsyncSoftTtlCacheHelper[A] =
    new AsyncSoftTtlCacheHelper[A](cache, logger, ttl)
}

/**
  * Wrapper around Play's AsyncCacheApi which stores an additional soft TTL.
  * If an item is retrieved that is older than this soft TTL, we update its
  * value in the background but return the stale value immediately.
  */
class AsyncSoftTtlCacheHelper[A: ClassTag](
  cache: AsyncCacheApi,
  logger: Logger,
  ttlStrategy: A => Ttl
)(implicit executor: ExecutionContext) {

  def getOrElseUpdate(key: String)(update: => Future[A]): Future[A] =
    getOrElseUpdateElement(key)(update)(CacheOptions.default).map(_.value)

  def getOrElseUpdateElement(key: String)(update: => Future[A])(implicit cacheOptions: CacheOptions): Future[CacheElement[A]] =
    if (cacheOptions.noCache) doUpdate(key)(update)
    else cache.get[CacheElement[A]](key).flatMap {
      case Some(element) =>
        if (element.isStale) {
          // try an update sy
          doUpdate(key)(update).fallbackTo(Future.successful(element))
        } else if(element.isSlightlyStale) {
          doUpdate(key)(update) // update the cache in the background
          Future.successful(element) // return the slightly stale value
        } else {
          element.value match {
            case _: A =>
              Future.successful(element)
            case _ =>
              logger.info(s"Incorrect type from cache fetching $key; doing update")
              doUpdate(key)(update)
          }
        }
      case None =>
        doUpdate(key)(update)
    }

  private def doUpdate(key: String)(update: => Future[A]): Future[CacheElement[A]] =
    update.flatMap { updateResult =>
      doSet(key, updateResult)
    }.recover {
      case e: Throwable =>
        throw new RuntimeException(s"Failure to retrieve new value for $key", e)
    }

  private def doSet(key: String, value: A): Future[CacheElement[A]] = {
    val ttl = ttlStrategy(value)
    val now = JavaTime.instant
    val softExpiry = now.plusSeconds(ttl.soft.toSeconds).getEpochSecond
    val mediumExpiry = now.plusSeconds(ttl.medium.toSeconds).getEpochSecond
    val element = CacheElement(value, now.getEpochSecond, softExpiry, mediumExpiry)
    cache.set(key, element, ttl.hard)
      .recover { case e: Throwable => logger.error(s"Failure to update cache for $key", e) }
      .map(_ => element)
  }

}
