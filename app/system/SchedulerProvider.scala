package system

import com.google.inject.{Inject, Provider, Singleton}
import javax.sql.DataSource
import org.quartz._
import org.quartz.impl.StdSchedulerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle
import play.api.libs.JNDI
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DataSourceExtractor {
  import scala.language.reflectiveCalls

  // runtime type, will basically use reflection to get at ds.
  // It's probably HikariCPJdbcDataSource but could be others, and there's no common trait.
  type HasDataSource = { def ds: javax.sql.DataSource }

  def extract(db: DatabaseConfigProvider): DataSource =
    db.get[JdbcProfile].db.source.asInstanceOf[HasDataSource].ds
}

@Singleton
class SchedulerProvider @Inject()(
  jobFactory: GuiceJobFactory,
  lifecycle: ApplicationLifecycle,
  @NamedDatabase("default") db: DatabaseConfigProvider
) extends Provider[Scheduler] {

  private def shutdown: Future[Unit] = Future {
    // Waits for running jobs to finish.
    scheduler.shutdown(true)
  }

  private lazy val scheduler = {
    // quartz.properties specifies this JNDI name
    JNDI.initialContext.rebind("db.default", DataSourceExtractor.extract(db))

    val s = new StdSchedulerFactory().getScheduler
    s.setJobFactory(jobFactory)

    lifecycle.addStopHook(shutdown _)
    s
  }

  def get(): Scheduler = scheduler
}
