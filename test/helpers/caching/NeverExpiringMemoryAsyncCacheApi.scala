package helpers.caching

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import akka.Done
import play.api.cache.AsyncCacheApi

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Success

/**
  * Stores stuff in memory. NOTE hard expiry is ignored!!
  *
  * Serializes objects to a byte array just like memcached will
  */
class NeverExpiringMemoryAsyncCacheApi extends AsyncCacheApi {
  private val data: mutable.Map[String, Array[Byte]] = mutable.Map()

  private def serialize(value: Any): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(value)
    oos.close()

    baos.toByteArray
  }

  private def deserialize[A](value: Array[Byte]): A = {
    val bais = new ByteArrayInputStream(value)
    val ois = new ObjectInputStream(bais)

    try {
      ois.readObject().asInstanceOf[A]
    } finally {
      ois.close()
    }
  }

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    data.put(key, serialize(value))
    Future.successful(Done)
  }

  override def remove(key: String): Future[Done] = {
    data.remove(key)
    Future.successful(Done)
  }

  override def getOrElseUpdate[A : ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] = {
    if (data.contains(key)) {
      Future.successful(deserialize(data(key)))
    } else {
      orElse.andThen {
        case Success(a) => set(key, a)
      }
    }
  }

  override def get[T : ClassTag](key: String): Future[Option[T]] = {
    Future.successful(data.get(key).map(deserialize[T]))
  }

  override def removeAll(): Future[Done] = {
    data.clear()
    Future.successful(Done)
  }
}