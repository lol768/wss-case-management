package domain

import java.time.OffsetDateTime

import akka.Done
import domain.CustomJdbcTypes._
import helpers.JavaTime
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Rep
import slick.sql.FixedSqlStreamingAction

import scala.concurrent.ExecutionContext

trait Versioned[A <: Versioned[A]] {
  def version: OffsetDateTime

  def atVersion(at: OffsetDateTime): A
  def storedVersion[B <: StoredVersion[A]](operation: DatabaseOperation, timestamp: OffsetDateTime): B
}

trait StoredVersion[A <: Versioned[A]] {
  def version: OffsetDateTime
  def operation: DatabaseOperation
  def timestamp: OffsetDateTime
}

trait VersionedTable[A <: Versioned[A]] {
  def version: Rep[OffsetDateTime]
  def matchesPrimaryKey(other: A): Rep[Boolean]
}

trait StoredVersionTable[A <: Versioned[A]] {
  def version: Rep[OffsetDateTime]
  def operation: Rep[DatabaseOperation]
  def timestamp: Rep[OffsetDateTime]
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

    def insert(value: A)(implicit ec: ExecutionContext): DBIO[A] = {
      // We ignore the version passed through
      val versionTimestamp = JavaTime.offsetDateTime
      val versionedValue = value.atVersion(versionTimestamp)
      val storedVersion = versionedValue.storedVersion[B](DatabaseOperation.Insert, versionTimestamp)

      for {
        inserted <- table += versionedValue if inserted == 1
        _ <- versionsTable += storedVersion
      } yield versionedValue
    }
    def +=(value: A)(implicit ec: ExecutionContext): DBIO[A] = insert(value)

    def insertAll(values: Seq[A])(implicit ec: ExecutionContext): DBIO[Seq[A]] = {
      // We ignore the versions passed through
      val versionTimestamp = JavaTime.offsetDateTime
      val versionedValues = values.map(_.atVersion(versionTimestamp))
      val storedVersions = versionedValues.map(_.storedVersion[B](DatabaseOperation.Insert, versionTimestamp))

      for {
        inserted <- table ++= versionedValues if inserted.isEmpty || inserted.contains(versionedValues.size)
        _ <- versionsTable ++= storedVersions
      } yield versionedValues
    }
    def ++=(values: Seq[A])(implicit ec: ExecutionContext): DBIO[Seq[A]] = insertAll(values)

    private[this] def optimisticLockingException(value: A)(implicit ec: ExecutionContext): DBIO[Nothing] =
      table.filter(_.matchesPrimaryKey(value)).result.flatMap {
        case Seq(current) => DBIO.failed(new Exception(s"Optimistic locking failed - tried to update version ${value.version} but current value is ${current.version}"))
        case _ => DBIO.failed(new Exception("Optimistic locking failed"))
      }

    def update(value: A)(implicit ec: ExecutionContext): DBIO[A] = {
      val originalVersion = value.version
      val versionTimestamp = JavaTime.offsetDateTime
      val versionedValue = value.atVersion(versionTimestamp)
      val storedVersion = versionedValue.storedVersion[B](DatabaseOperation.Update, versionTimestamp)

      val updateAction =
        table.filter { a => a.matchesPrimaryKey(value) && a.version === originalVersion }.update(versionedValue).flatMap {
          case 1 => DBIO.successful(versionedValue)
          case _ => optimisticLockingException(value)
        }

      for {
        updated <- updateAction
        _ <- versionsTable += storedVersion
      } yield updated
    }

    def delete(value: A)(implicit ec: ExecutionContext): DBIO[Done] = {
      val storedVersion = value.storedVersion[B](DatabaseOperation.Delete, JavaTime.offsetDateTime)

      val deleteAction =
        table.filter { a => a.matchesPrimaryKey(value) && a.version === value.version }.delete.flatMap {
          case 1 => DBIO.successful(Done)
          case _ => optimisticLockingException(value)
        }

      for {
        deleted <- deleteAction
        _ <- versionsTable += storedVersion
      } yield deleted
    }
    def -=(value: A)(implicit ec: ExecutionContext): DBIO[Done] = delete(value)
  }
}