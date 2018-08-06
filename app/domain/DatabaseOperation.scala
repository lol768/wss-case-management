package domain

import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

sealed trait DatabaseOperation extends EnumEntry

object DatabaseOperation extends Enum[DatabaseOperation] {
  case object Insert extends DatabaseOperation
  case object Update extends DatabaseOperation
  case object Delete extends DatabaseOperation

  override val values: immutable.IndexedSeq[DatabaseOperation] = findValues
}
