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

import scala.concurrent.{ExecutionContext, Future}

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

  private lazy val scheduler = {
    val s = new StdSchedulerFactory().getScheduler
    s.setJobFactory(jobFactory)
    s
  }


  def get(): Scheduler = {
    import ExecutionContext.Implicits.global

    // quartz.properties specifies this JNDI name
    JNDI.initialContext.rebind("db.default", DataSourceExtractor.extract(db))

    def shutdown: Future[Unit] = Future {
      // Waits for running jobs to finish.
      scheduler.shutdown(true)
    }

    lifecycle.addStopHook(shutdown _)

    scheduler
  }
}
