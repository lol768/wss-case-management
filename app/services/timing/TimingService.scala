package services.timing

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import services.timing.TimingService.Category

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TimingServiceImpl])
trait TimingService {
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