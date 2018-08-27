package services

import TimingService.Category
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

/**
  * TimingContext is an object passed around to track how long things take during
  * a request or other non-request operation. Normally the context will be an implicit
  * RequestContext or AuditLogContext, so if your method already has an implicit one of
  * those passed in then it should just work. It's best to declare an implicit TimingContext
  * rather than one of the specific contexts unless you specifically need them.
  */
@implicitNotFound(
  "Couldn't find a TimingContext for timing. Usually this will be an " +
  "implicit RequestContext or AuditLogContext which both implement TimingContext."
)
trait TimingContext {
  import TimingContext._
  def timingData: Data
}

object TimingContext {
  case class DataItem(category: Category, start: Long, end: Long) {
    def time: Long = end - start
  }

  class Data {
    val items: collection.mutable.ListBuffer[DataItem] = collection.mutable.ListBuffer()

    def profileSummary: Map[Category, Long] =
      items.foldLeft(Map[Category, Long]()) { (map, item) =>
        val key = item.category
        map.updated(key, map.getOrElse(key, 0L) + item.time)
      }
  }

  val none: TimingContext = new TimingContext {
    override def timingData: Data = new Data()
  }
}

@ImplementedBy(classOf[TimingServiceImpl])
trait TimingService {
  // TODO typed category
  // TODO add secondary non-Future method (timeSync?)
  def time[T](category: Category*)(fn: => Future[T])(implicit ctx: TimingContext): Future[T]
}

@Singleton
class TimingServiceImpl @Inject() (
  implicit ec: ExecutionContext
) extends TimingService {
  def time[T](category: Category*)(fn: => Future[T])(implicit ctx: TimingContext): Future[T] = {
    if (ctx == TimingContext.none) {
      fn
    } else {
      val start: Long = System.currentTimeMillis()
      fn.andThen { case _ =>
        val end = System.currentTimeMillis()
        for (top <- category; c <- top.allCategories)
          ctx.timingData.items += TimingContext.DataItem(c, start, end)
      }
    }
  }

}

object TimingService {
  abstract class Category(val id: String, description: Option[String] = None, inherits: Seq[Category] = Nil) {
    lazy val allCategories: Seq[Category] = inherits :+ this
  }
}