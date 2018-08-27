package services.timing

import services.timing.TimingService.Category

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
    private[timing] val items: collection.mutable.ListBuffer[DataItem] = collection.mutable.ListBuffer()

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
