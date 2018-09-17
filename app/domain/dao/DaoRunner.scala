package domain.dao

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import warwick.core.timing.{TimingContext, TimingService}
import slick.jdbc.JdbcProfile
import system.TimingCategories

import scala.concurrent.{ExecutionContext, Future}

/**
  * Runs DB actions that have come from elsewhere.
  */
@ImplementedBy(classOf[DaoRunnerImpl])
trait DaoRunner {
  import slick.dbio._

  /**
    * Runs the given action in a transaction
    */
  def run[R](a: DBIO[R])(implicit t: TimingContext): Future[R]
}

@Singleton
class DaoRunnerImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  timing: TimingService,
)(
  implicit ec: ExecutionContext
) extends DaoRunner with HasDatabaseConfigProvider[JdbcProfile] {
  import profile.api._
  import timing._

  override def run[R](a: DBIO[R])(implicit t: TimingContext): Future[R] = time(TimingCategories.Db) {
    db.run(a.transactionally)
  }
}
