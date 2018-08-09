package helpers.caching

import helpers.JavaTime
import play.api.Logger
import play.api.cache.AsyncCacheApi

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object VariableTtlCacheHelper {
  def async[A: ClassTag](cache: AsyncCacheApi, logger: Logger, softTtl: FiniteDuration, mediumTtl: FiniteDuration, hardTtl: Duration)(implicit executor: ExecutionContext): AsyncVariableTtlCacheHelper[A] = {
    val ttl = Ttl(softTtl, mediumTtl, hardTtl)
    new AsyncVariableTtlCacheHelper[A](cache, logger, _ => ttl)
  }

  def async[A: ClassTag](cache: AsyncCacheApi, logger: Logger, ttl: A => Ttl)(implicit executor: ExecutionContext): AsyncVariableTtlCacheHelper[A] =
    new AsyncVariableTtlCacheHelper[A](cache, logger, ttl)
}

/**
  * Wrapper around Play's AsyncCacheApi which stores an additional soft TTL.
  * If an item is retrieved that is older than this soft TTL, we update its
  * value in the background but return the stale value immediately.
  */
class AsyncVariableTtlCacheHelper[A: ClassTag](
  cache: AsyncCacheApi,
  logger: Logger,
  ttlStrategy: A => Ttl
)(implicit executor: ExecutionContext) {

  def getOrElseUpdate(key: String)(update: => Future[A]): Future[A] =
    getOrElseUpdateElement(key)(update)(CacheOptions.default).map(_.value)

  def getOrElseUpdateElement(key: String)(update: => Future[A])(implicit cacheOptions: CacheOptions): Future[CacheElement[A]] = {

    def validateCachedValueType(element: CacheElement[A]):Future[CacheElement[A]] = {
      element.value match {
        case _: A =>
          Future.successful(element)
        case _ =>
          logger.info(s"Incorrect type from cache fetching $key; doing update")
          doUpdate(key)(update)
      }
    }

    if (cacheOptions.noCache) doUpdate(key)(update)
    else cache.get[CacheElement[A]](key).flatMap {
      case Some(element) =>
        if (element.isStale) {
          // try to get a fresh value but return the cached value if that fails
          doUpdate(key)(update).fallbackTo(validateCachedValueType(element))
        } else if (element.isSlightlyStale) {
          // update the cache in the background
          doUpdate(key)(update).failed.foreach(t =>
            logger.error(s"Background cache update for $key failed. ${t.getMessage}")
          )
          // return the slightly stale value
          validateCachedValueType(element)
        } else {
          validateCachedValueType(element)
        }
      case None =>
        doUpdate(key)(update)
    }
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
