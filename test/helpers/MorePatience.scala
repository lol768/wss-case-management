package helpers

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Span}

trait MorePatience {

  self: PatienceConfiguration =>

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(1000, Millis)), scaled(Span(15, Millis)))

}
