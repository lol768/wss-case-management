package domain

import java.time.ZonedDateTime

import akka.Done
import domain.CustomJdbcTypes._
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Rep
import slick.sql.FixedSqlStreamingAction

import scala.concurrent.ExecutionContext

trait Versioned[A <: Versioned[A]] {
  def version: ZonedDateTime

  def atVersion(at: ZonedDateTime): A
  def storedVersion[B <: StoredVersion[A]](operation: DatabaseOperation, timestamp: ZonedDateTime): B
}

trait StoredVersion[A <: Versioned[A]] {
  def version: ZonedDateTime
  def operation: DatabaseOperation
  def timestamp: ZonedDateTime
}

trait VersionedTable[A <: Versioned[A]] {
  def version: Rep[ZonedDateTime]
  def matchesPrimaryKey(other: A): Rep[Boolean]
}

trait StoredVersionTable[A <: Versioned[A]] {
  def version: Rep[ZonedDateTime]
  def operation: Rep[DatabaseOperation]
  def timestamp: Rep[ZonedDateTime]
}

trait Versioning {
  object VersionedTableQuery {
    def apply[A <: Versioned[A], B <: StoredVersion[A], C <: Table[A] with VersionedTable[A], D <: Table[B] with StoredVersionTable[A]](table: TableQuery[C], versionsTable: TableQuery[D]) =
      new VersionedTableQuery[A, B, C, D](table, versionsTable)
  }

  class VersionedTableQuery[A <: Versioned[A], B <: StoredVersion[A], C <: Table[A] with VersionedTable[A], D <: Table[B] with StoredVersionTable[A]](
    val table: TableQuery[C],
    val versionsTable: TableQuery[D]
  ) {
    def result: FixedSqlStreamingAction[Seq[A], A, Effect.Read] = table.result

    def insert(value: A)(implicit ec: ExecutionContext): DBIOAction[A, NoStream, Effect.Write] = {
      // We ignore the version passed through
      val versionTimestamp = ZonedDateTime.now()
      val versionedValue = value.atVersion(versionTimestamp)
      val storedVersion = versionedValue.storedVersion[B](DatabaseOperation.Insert, versionTimestamp)

      for {
        inserted <- table += versionedValue if inserted == 1
        _ <- versionsTable += storedVersion
      } yield versionedValue
    }
    def +=(value: A)(implicit ec: ExecutionContext): DBIOAction[A, NoStream, Effect.Write] = insert(value)

    def update(value: A)(implicit ec: ExecutionContext): DBIOAction[A, NoStream, Effect.Write] = {
      val originalVersion = value.version
      val versionTimestamp = ZonedDateTime.now()
      val versionedValue = value.atVersion(versionTimestamp)
      val storedVersion = versionedValue.storedVersion[B](DatabaseOperation.Update, versionTimestamp)

      val updateAction =
        table.filter { a => a.matchesPrimaryKey(value) && a.version === originalVersion }.update(versionedValue).flatMap {
          case 1 => DBIO.successful(versionedValue)
          case _ => DBIO.failed(new Exception("Optimistic locking failed")) // Rollback
        }

      for {
        updated <- updateAction
        _ <- versionsTable += storedVersion
      } yield updated
    }

    def delete(value: A)(implicit ec: ExecutionContext): DBIOAction[Done, NoStream, Effect.Write] = {
      val storedVersion = value.storedVersion[B](DatabaseOperation.Delete, ZonedDateTime.now())

      val deleteAction =
        table.filter { a => a.matchesPrimaryKey(value) && a.version === value.version }.delete.flatMap {
          case 1 => DBIO.successful(Done)
          case _ => DBIO.failed(new Exception("Optimistic locking failed")) // Rollback
        }

      for {
        deleted <- deleteAction
        _ <- versionsTable += storedVersion
      } yield deleted
    }
    def -=(value: A)(implicit ec: ExecutionContext): DBIOAction[Done, NoStream, Effect.Write] = delete(value)
  }
}