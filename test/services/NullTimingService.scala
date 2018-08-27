package services
import services.timing.{TimingContext, TimingService}
import services.timing.TimingService.Category

import scala.concurrent.Future

class NullTimingService extends TimingService {
  override def time[T](category: Category*)(fn: => Future[T])(implicit ctx: TimingContext): Future[T] = fn
}
