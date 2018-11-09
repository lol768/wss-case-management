package system

import akka.Done
import com.google.inject.{AbstractModule, Singleton}
import play.api.cache.{AsyncCacheApi, SyncCacheApi}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

@Singleton
class NullCacheApi extends AsyncCacheApi {

  private val done = Future.successful(Done)

  def set(key: String, value: Any, expiration: Duration = Duration.Inf): Future[Done] =
    done

  def remove(key: String): Future[Done] =
    done

  def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration = Duration.Inf)(orElse: => Future[A]): Future[A] =
    orElse

  def get[T: ClassTag](key: String): Future[Option[T]] =
    Future.successful(None)

  def removeAll(): Future[Done] =
    done
}

class NullCacheModule extends AbstractModule {

  override def configure() = {
    val cache = new NullCacheApi()
    bind(classOf[AsyncCacheApi]).toInstance(cache)
    bind(classOf[SyncCacheApi]).toInstance(cache.sync)
  }

}