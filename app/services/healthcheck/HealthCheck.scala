package services.healthcheck

import java.time.LocalDateTime

import play.api.libs.json.{JsObject, Json}
import services.healthcheck.HealthCheckStatus.{Critical, Okay, Warning}

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
}

abstract class HealthCheck[T](implicit num: Numeric[T]) {

  def name: String

  def status: HealthCheckStatus = {
    val higherIsBetter = num.lt(critical, warning)

    if (higherIsBetter) {
      if (num.gt(value, warning)) Okay
      else if (num.gt(value, critical)) Warning
      else Critical
    } else {
      if (num.lt(value, warning)) Okay
      else if (num.lt(value, critical)) Warning
      else Critical
    }
  }

  def message: String

  def value: T

  def warning: T

  def critical: T

  def perfData: Seq[PerfData[T]] = Seq()

  def testedAt: LocalDateTime

  def toJson: JsObject = Json.obj(
    "name" -> name,
    "status" -> status.string,
    "perfData" -> (Seq(PerfData(name.replace("-", "_"), value, Some(warning), Some(critical)).formatted) ++ perfData.map(_.formatted)),
    "message" -> s"$message (warning: $warning, critical: $critical)",
    "testedAt" -> testedAt
  )

}
