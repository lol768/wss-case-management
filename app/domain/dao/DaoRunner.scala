package domain.dao

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.{TimingCategories, TimingContext, TimingService}

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

  /**
    * Runs the given action in a transaction and wrap in a ServiceResult
    */
  def runWithServiceResult[R](a: DBIO[R])(implicit t: TimingContext): Future[ServiceResult[R]]
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

  override def runWithServiceResult[R](a: DBIO[R])(implicit t: TimingContext): Future[ServiceResult[R]] = time(TimingCategories.Db) {
    run(a).map(ServiceResults.success).recoverWith {
      case error: ServiceResults.ServiceResultException =>
        Future.successful(Left(error.errors))
      case error =>
        throw error
    }
  }
}
