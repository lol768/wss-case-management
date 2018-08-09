package domain.dao

import helpers.{DaoPatience, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import slick.dbio.DBIOAction

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

case class IntentionalRollbackException[R](successResult: R) extends Exception("Rolling back transaction")

abstract class AbstractDaoTest extends PlaySpec with MockitoSugar with OneAppPerSuite with ScalaFutures with DaoPatience {

  implicit val ec = get[ExecutionContext]

  private val runner = get[DaoRunner]

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
  def runWithRollback[R, S <: NoStream, E <: Effect](action: DBIOAction[R, S, E]): Future[R] = {
    val block = action.flatMap(r => DBIOAction.failed(new IntentionalRollbackException(r)))
    runner.run(block.transactionally.withPinnedSession).failed.map {
      case e: IntentionalRollbackException[_] => e.successResult.asInstanceOf[R]
      case t => throw t
    }
  }



}
