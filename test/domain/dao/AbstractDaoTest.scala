package domain.dao

import helpers.{DaoPatience, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.DatabaseConfig
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class IntentionalRollbackException[R](successResult: R) extends Exception("Rolling back transaction")

abstract class AbstractDaoTest extends PlaySpec with MockitoSugar with OneAppPerSuite with ScalaFutures with DaoPatience {

  implicit lazy val ec = get[ExecutionContext]

  private lazy val runner = get[DaoRunner]

  protected lazy val dbConfigProvider: DatabaseConfigProvider = get[DatabaseConfigProvider]
  protected lazy val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  protected lazy val profile: JdbcProfile = dbConfig.profile

  // Some aliases to allow tests to be a bit tidier
  lazy val DBIO = profile.api.DBIO

  /**
    * This is how to write DAO tests that roll back.
    *
    * https://stackoverflow.com/a/34953817
    *
    * You can mingle test code in between DB actions by putting it in DBIOAction.from(Future {})
    *
    * At the moment you have to remember to do this every time. It might be possible to work this
    * in as a configuration option on `DaoRunner`, or even by wrapping the `Database` with something
    * to intercept all actions.
    */
  def runWithRollback[R](action: DBIO[R]): Future[R] = {
    import profile.api._
    val block = action.flatMap(r => DBIOAction.failed(IntentionalRollbackException(r)))
    runner.run(block.transactionally).failed.map {
      case e: IntentionalRollbackException[_] => e.successResult.asInstanceOf[R]
      case t => throw t
    }
  }

  /**
    * Convenience synchronous version of [[runWithRollback()]]
    */
  def exec[R](action: DBIO[R]): R = Await.result(runWithRollback(action), 5.seconds)

  def execWithCommit[R](action: DBIO[R]): R = Await.result(runner.run(action), 5.seconds)

}
