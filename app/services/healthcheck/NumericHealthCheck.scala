package services.healthcheck

import java.time.OffsetDateTime

import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.ServiceHealthcheck.Status._
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.helpers.JavaTime.{localDateTime => now}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

abstract class NumericHealthCheck[T](val name: String)(implicit num: Numeric[T])
  extends ServiceHealthcheckProvider(new ServiceHealthcheck(name, Status.Unknown, now)) {

  def status: ServiceHealthcheck.Status = {
    val higherIsBetter = num.lt(critical, warning)
    val currentValue = value

    if (higherIsBetter) {
      if (num.gt(currentValue, warning)) Okay
      else if (num.gt(currentValue, critical)) Warning
      else Error
    } else {
      if (num.lt(currentValue, warning)) Okay
      else if (num.lt(currentValue, critical)) Warning
      else Error
    }
  }

  def message: String
  def value: T
  def warning: T
  def critical: T
  def perfData: Seq[ServiceHealthcheck.PerformanceData[T]] = Seq()
  def testedAt: OffsetDateTime

  override def run(): Unit = update({
    Try(
      new ServiceHealthcheck(
        name,
        status,
        now,
        message,
        perfData.asInstanceOf[Seq[ServiceHealthcheck.PerformanceData[_]]].asJava
      )
    ) match {
      case Success(hc) => hc
      case Failure(t) => new ServiceHealthcheck(name, Unknown, now, s"Error performing health check: ${t.getMessage}")
    }
  })

}
