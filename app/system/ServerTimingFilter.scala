package system

import akka.stream.Materializer
import javax.inject.Inject
import play.api.libs.typedmap.TypedKey
import play.api.mvc.{Filter, RequestHeader, Result}
import play.api.routing.Router
import services.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

object ServerTimingFilter {
  val TimingData: TypedKey[TimingContext.Data] = TypedKey("timingContextData")
}

class ServerTimingFilter @Inject() (
  implicit val mat: Materializer,
  ec: ExecutionContext
) extends Filter {

  import ServerTimingFilter._

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    if (rh.attrs.get(Router.Attrs.HandlerDef).exists(_.modifiers.contains("notiming"))) {
      f(rh)
    } else {
      val data = new TimingContext.Data()
      f(rh.addAttr(TimingData, data)).map { result =>
        getHeader(data).fold(result)(result.withHeaders(_))
      }
    }
  }

  def getHeader(data: TimingContext.Data): Option[(String, String)] =
    Option(data.profileSummary).filterNot(_.isEmpty).map { summary =>
      "Server-Timing" -> summary.map { case (category, time) =>
        s"${category.id};dur=$time"
      }.mkString(", ")
    }

}
