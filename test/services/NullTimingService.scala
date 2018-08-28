package services

import warwick.core.timing.TimingContext.Category
import warwick.core.timing._

import scala.concurrent.Future

class NullTimingService extends TimingService {
  override def time[T](category: Category*)(fn: => Future[T])(implicit ctx: TimingContext): Future[T] = fn
  override def timeSync[T](category: Category*)(fn: => T)(implicit ctx: TimingContext): T = fn
}
