package services.healthcheck

import java.util.concurrent.{ExecutorService, ThreadPoolExecutor}

import akka.actor.ActorSystem
import akka.dispatch.{Dispatcher, ExecutorServiceDelegate, ForkJoinExecutorConfigurator}
import javax.inject.{Inject}
import org.slf4j.{Logger, LoggerFactory}
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.helpers.JavaTime.{localDateTime => now}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import ThreadPoolHealthCheck._

object ThreadPoolHealthCheck {
  def name(id:String) = s"thread-pool-${id.toLowerCase}"
}

class ThreadPoolHealthCheck(id: String)
  extends ServiceHealthcheckProvider(new ServiceHealthcheck(name(id), Status.Unknown, now)) {

  @Inject private var actorSystem: ActorSystem = _

  // The thread pool we're inspecting
  private lazy val dispatcher = id match {
    case "default" => actorSystem.dispatcher
    case _ => actorSystem.dispatchers.lookup(s"threads.$id")
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

  override def run(): Unit = update({
    val executorServiceMethod = classOf[Dispatcher].getDeclaredMethod("executorService")
    executorServiceMethod.setAccessible(true)

    val executor: ExecutorService = executorServiceMethod.invoke(dispatcher).asInstanceOf[ExecutorServiceDelegate].executor
    val (status, message, perfData) = executor match {
      case pool: ForkJoinExecutorConfigurator.AkkaForkJoinPool =>
        val status =
          if (pool.getActiveThreadCount == pool.getParallelism) Status.Warning
          else Status.Okay

        val message = s"fork-join-executor, parallelism=${pool.getParallelism}, ${pool.getActiveThreadCount} active thread${if (pool.getActiveThreadCount == 1) "" else "s"}"
        val perfData = Seq(
          new ServiceHealthcheck.PerformanceData("activeThreadCount", pool.getActiveThreadCount, pool.getParallelism, pool.getParallelism, 0, pool.getParallelism),
          new ServiceHealthcheck.PerformanceData("poolSize", pool.getPoolSize),
          new ServiceHealthcheck.PerformanceData("queuedSubmissionCount", pool.getQueuedSubmissionCount),
          new ServiceHealthcheck.PerformanceData("queuedTaskCount", pool.getQueuedTaskCount),
          new ServiceHealthcheck.PerformanceData("runningThreadCount", pool.getRunningThreadCount),
          new ServiceHealthcheck.PerformanceData("stealCount", pool.getStealCount)
        )

        (status, message, perfData)

      case pool: ThreadPoolExecutor =>
        val (warn, crit) = ((0.9 * pool.getMaximumPoolSize).toInt, (0.95 * pool.getMaximumPoolSize).toInt)

        val status =
          if (pool.getActiveCount >= crit) Status.Error
          else if (pool.getActiveCount >= warn) Status.Warning
          else Status.Okay
        val message = s"thread-pool-executor, poolSize=${pool.getPoolSize}, maxPoolSize=${pool.getMaximumPoolSize}, ${pool.getActiveCount} active thread${if (pool.getActiveCount == 1) "" else "s"} (warn: $warn, crit: $crit)"

        val perfData = Seq(
          new ServiceHealthcheck.PerformanceData("activeCount", pool.getActiveCount, warn, crit, 0, pool.getMaximumPoolSize),
          new ServiceHealthcheck.PerformanceData("completedTaskCount", pool.getCompletedTaskCount),
          new ServiceHealthcheck.PerformanceData("corePoolSize", pool.getCorePoolSize),
          new ServiceHealthcheck.PerformanceData("largestPoolSize", pool.getLargestPoolSize),
          new ServiceHealthcheck.PerformanceData("poolSize", pool.getPoolSize),
          new ServiceHealthcheck.PerformanceData("queueSize", pool.getQueue.size),
          new ServiceHealthcheck.PerformanceData("taskCount", pool.getTaskCount)
        )

        (status, message, perfData)

      case _ => (Status.Warning, executor.getClass.getName, Nil)
    }

    new ServiceHealthcheck(name(id), status, now, message, perfData.asInstanceOf[Seq[ServiceHealthcheck.PerformanceData[_]]].asJava)
  })

  // Called by AppStartup (Guice has no PostConstruct support)
  def init(): Unit = {
    actorSystem.scheduler.schedule(0.seconds, 5.seconds) {
      try run()
      catch {
        case e: Throwable =>
          logger.error("Error in health check", e)
      }
    }(actorSystem.dispatcher)
  }
}
