package helpers

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Span}

/**
  * Quite lax patience to account for H2 sometimes being slow to get going.
  */
trait DaoPatience {
  self: PatienceConfiguration =>

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(5000, Millis)), scaled(Span(15, Millis)))

}
