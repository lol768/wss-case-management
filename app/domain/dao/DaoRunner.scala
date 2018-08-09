package domain.dao

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

/**
  * Runs DB actions that have come from elsewhere.
  */
@ImplementedBy(classOf[DaoRunnerImpl])
trait DaoRunner {
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R]
}

@Singleton
class DaoRunnerImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
) extends DaoRunner with HasDatabaseConfigProvider[JdbcProfile] {

  override def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)

}
