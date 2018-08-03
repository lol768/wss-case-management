package services.healthcheck

import java.time.LocalDateTime

import play.api.libs.json.{JsObject, Json}
import services.healthcheck.HealthCheckStatus.{Critical, Okay, Warning}

import scala.util.{Failure, Success, Try}

case class HealthCheckStatus(string: String)

case class PerfData[T](name: String, value: T, warn: Option[T] = None, critical: Option[T] = None, min: Option[T] = None, max: Option[T] = None) {

  def formatted: String = {
    val warnCritical = (for (a <- warn; b <- critical) yield s";$a;$b").getOrElse("")
    val minMax = (for (a <- min; b <- max) yield s";$a;$b").getOrElse("")

    s"$name=$value$warnCritical$minMax"
  }

}

object HealthCheckStatus {
  val Okay = HealthCheckStatus("okay")
  val Warning = HealthCheckStatus("warning")
  val Critical = HealthCheckStatus("critical")
  val Unknown = HealthCheckStatus("unknown")
}

trait HealthCheck {
  def name: String
  def toJson: JsObject
}

abstract class NumericHealthCheck[T](implicit num: Numeric[T]) extends HealthCheck {

  override def name: String

  def status: HealthCheckStatus = {
    val higherIsBetter = num.lt(critical, warning)
    val currentValue = value

    if (higherIsBetter) {
      if (num.gt(currentValue, warning)) Okay
      else if (num.gt(currentValue, critical)) Warning
      else Critical
    } else {
      if (num.lt(currentValue, warning)) Okay
      else if (num.lt(currentValue, critical)) Warning
      else Critical
    }
  }

  def message: String

  def value: T

  def warning: T

  def critical: T

  def perfData: Seq[PerfData[T]] = Seq()

  def testedAt: LocalDateTime

  override def toJson: JsObject =
    Try(Json.obj(
      "name" -> name,
      "status" -> status.string,
      "perfData" -> (Seq(PerfData(name.replace("-", "_"), value, Some(warning), Some(critical)).formatted) ++ perfData.map(_.formatted)),
      "message" -> s"$message (warning: $warning, critical: $critical)",
      "testedAt" -> testedAt
    )) match {
      case Success(json) => json
      case Failure(t) => Json.obj(
        "name" -> name,
        "status" -> HealthCheckStatus.Unknown.string,
        "message" -> s"Error performing health check: ${t.getMessage}",
        "testedAt" -> LocalDateTime.now
      )
    }

}
