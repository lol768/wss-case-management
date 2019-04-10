package services.job

import helpers.ServiceResults.ServiceError
import org.quartz._
import services.AuditLogContext
import warwick.core.Logging

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}

sealed trait JobResult
object JobResult {
  case object quiet extends JobResult
  case class success(summary: String) extends JobResult
  case class failure(summary: String) extends JobResult

  object failure {
    def apply(errors: List[_ <: ServiceError]): failure =
      failure(errors.map(_.message).mkString(", "))
  }
}

abstract class AbstractJob(scheduler: Scheduler) extends Job with Logging {
  val timeout: Duration = Duration.Inf

  // Some jobs don't benefit from too much logging - they can do their own
  val doLog: Boolean = true

  def run(implicit context: JobExecutionContext, audit: AuditLogContext): Future[JobResult]

  def getDescription(context: JobExecutionContext): String

  override final def execute(context: JobExecutionContext): Unit = {
    val audit = AuditLogContext.empty()
    val description = getDescription(context)
    try {
      if (doLog) logger.info(s"Starting job: $description")
      val result = Await.result(run(context, audit), timeout)
      if (doLog) logger.info(s"Completed job: $description with result: $result")
    } catch {
      case e: TimeoutException =>
        if (doLog) logger.error(s"Job timed out after ${timeout.toCoarsest}: $description")
        throw new JobExecutionException(e)
      case e: Exception =>
        if (doLog) logger.error(s"Error in job: $description", e)
        throw new JobExecutionException(e)
    }
  }
}