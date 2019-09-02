package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  case object Message extends UploadedFileOwner
  case object MessageSnippet extends UploadedFileOwner

  val values: immutable.IndexedSeq[UploadedFileOwner] = findValues
}
