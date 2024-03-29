package domain

import java.time.OffsetDateTime

import akka.Done
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import services.AuditLogContext
import slick.dbio.Effect
import slick.lifted.{CanBeQueryCondition, Rep}
import slick.sql.FixedSqlStreamingAction
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait Versioned[A <: Versioned[A]] {
  def version: OffsetDateTime

  def atVersion(at: OffsetDateTime): A
  def storedVersion[B <: StoredVersion[A]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B
}

trait StoredVersion[A <: Versioned[A]] {
  def version: OffsetDateTime
  def operation: DatabaseOperation
  def timestamp: OffsetDateTime
  def auditUser: Option[Usercode]
}

trait VersionedTable[A <: Versioned[A]] {
  def version: Rep[OffsetDateTime]
  def matchesPrimaryKey(other: A): Rep[Boolean]
}

trait StoredVersionTable[A <: Versioned[A]] {
  def version: Rep[OffsetDateTime]
  def operation: Rep[DatabaseOperation]
  def timestamp: Rep[OffsetDateTime]
  def auditUser: Rep[Option[Usercode]]
}

object VersionedTableQuery {
  def apply[A <: Versioned[A], B <: StoredVersion[A], C <: Table[A] with VersionedTable[A], D <: Table[B] with StoredVersionTable[A]](table: TableQuery[C], versionsTable: TableQuery[D]) =
    new VersionedTableQuery[A, B, C, D](table, versionsTable)
}

class VersionedTableQuery[A <: Versioned[A], B <: StoredVersion[A], C <: Table[A] with VersionedTable[A], D <: Table[B] with StoredVersionTable[A]](
  val table: TableQuery[C],
  val versionsTable: TableQuery[D]
) {
  def result: FixedSqlStreamingAction[Seq[A], A, Effect.Read] = table.result

  def insert(value: A)(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[A] = {
    // We ignore the version passed through
    val versionTimestamp = JavaTime.offsetDateTime
    val versionedValue = value.atVersion(versionTimestamp)
    val storedVersion = versionedValue.storedVersion[B](DatabaseOperation.Insert, versionTimestamp)

    for {
      inserted <- table += versionedValue if inserted == 1
      _ <- versionsTable += storedVersion
    } yield versionedValue
  }
  def +=(value: A)(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[A] = insert(value)

  def insertAll(values: Seq[A])(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[Seq[A]] = {
    // We ignore the versions passed through
    val versionTimestamp = JavaTime.offsetDateTime
    val versionedValues = values.map(_.atVersion(versionTimestamp))
    val storedVersions = versionedValues.map(_.storedVersion[B](DatabaseOperation.Insert, versionTimestamp))

    for {
      inserted <- table ++= versionedValues if inserted.isEmpty || inserted.contains(versionedValues.size)
      _ <- versionsTable ++= storedVersions
    } yield versionedValues
  }
  def ++=(values: Seq[A])(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[Seq[A]] = insertAll(values)

  private[this] def optimisticLockingException(value: A)(implicit ec: ExecutionContext): DBIO[Nothing] =
    table.filter(_.matchesPrimaryKey(value)).result.flatMap {
      case Seq(current) => DBIO.failed(new Exception(s"Optimistic locking failed - tried to update version ${value.version} but current value is ${current.version}"))
      case _ => DBIO.failed(new Exception("Optimistic locking failed"))
    }

  def update(value: A)(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[A] = {
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

  def delete(value: A)(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[Done] = {
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
  def -=(value: A)(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[Done] = delete(value)

  def deleteAll(values: Seq[A])(implicit ec: ExecutionContext, ac: AuditLogContext): DBIO[Done] = {
    val versionTimestamp = JavaTime.offsetDateTime
    val storedVersions = values.map(_.storedVersion[B](DatabaseOperation.Delete, versionTimestamp))

    val deleteAction = DBIO.sequence(values.map(value =>
      table.filter { a => a.matchesPrimaryKey(value) && a.version === value.version }.delete.flatMap {
        case 1 => DBIO.successful(Done)
        case _ => optimisticLockingException(value)
      }
    )).map(_.partition {
      case Done => true
      case _ => false
    } match {
      case (_, Nil) => Done
      case (errors, _) => errors.head
    })

    for {
      delete <- deleteAction
      _ <- versionsTable ++= storedVersions
    } yield delete
  }

  def history[T <: Rep[_]](idFilter: D => T)(implicit wt: CanBeQueryCondition[T]): DBIO[Seq[B]] =
    this.versionsTable
      .filter(idFilter)
      .filter(e =>
          e.operation === (DatabaseOperation.Insert:DatabaseOperation) ||
            e.operation === (DatabaseOperation.Update:DatabaseOperation)
      )
      .sortBy(_.timestamp)
      .result
}

trait Versioning {
  type VersionedTableQuery[A <: Versioned[A], B <: StoredVersion[A], C <: Table[A] with VersionedTable[A], D <: Table[B] with StoredVersionTable[A]] =
    domain.VersionedTableQuery[A, B, C, D]
}