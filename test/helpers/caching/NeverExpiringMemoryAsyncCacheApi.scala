package helpers.caching

import akka.Done
import play.api.cache.AsyncCacheApi

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
  * Stores stuff in memory. NOTE hard expiry is ignored!!
  */
class NeverExpiringMemoryAsyncCacheApi extends AsyncCacheApi {
  private val data: mutable.Map[String, Any] = mutable.Map()

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    data.put(key, value)
    Future.successful(Done)
  }

  override def remove(key: String): Future[Done] = {
    data.remove(key)
    Future.successful(Done)
  }

  override def getOrElseUpdate[A : ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] = {
    Future.successful(data.getOrElseUpdate(key, orElse).asInstanceOf[A])
  }

  override def get[T : ClassTag](key: String): Future[Option[T]] = {
    Future.successful(data.get(key).map(_.asInstanceOf[T]))
  }

  override def removeAll(): Future[Done] = {
    data.clear()
    Future.successful(Done)
  }
}