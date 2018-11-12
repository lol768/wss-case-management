package domain

import enumeratum.EnumEntry
import org.scalatestplus.play.PlaySpec
import play.api.data.Forms._

import scala.collection.immutable

class PlayEnumWithOtherTest extends PlaySpec {

  sealed abstract class TestEnumEntry(val description: String) extends EnumEntry
  object TestEnumEntry extends PlayEnumWithOther[TestEnumEntry] {
    case object TestA extends TestEnumEntry("Test A")
    case object TestB extends TestEnumEntry("Test B")

    case class Other(override val value: Option[String]) extends TestEnumEntry("Other") with EnumEntryOther {
      override val label: String = "Other"
      override val description: String = s"$label (${value.orNull})"
    }

    override def otherBuilder[O <: EnumEntryOther](otherValue: Option[String]): O = Other(otherValue).asInstanceOf[O]

    override def nonOtherValues: immutable.IndexedSeq[TestEnumEntry] = findValues
  }

  "PlayEnumWithOther" should {

    "build entries" in {
      TestEnumEntry.apply(List("TestA", "TestB", "Other"), Some("Other value")) mustBe Set(
        TestEnumEntry.TestA,
        TestEnumEntry.TestB,
        TestEnumEntry.Other(Some("Other value"))
      )
    }

    "get other value from entries" in {
      TestEnumEntry.otherValue(Set(
        TestEnumEntry.TestA,
        TestEnumEntry.TestB,
        TestEnumEntry.Other(Some("Other value"))
      )) mustBe Some("Other value")
    }

    "include other in values" in {
      val result = TestEnumEntry.values
      result.size mustBe 3
      result.contains(TestEnumEntry.TestA) mustBe true
      result.contains(TestEnumEntry.TestB) mustBe true
      result.contains(TestEnumEntry.Other(None)) mustBe true
    }

    "bind mapping correctly" in {
      val form = single("test" -> TestEnumEntry.formMapping)

      val unknownEntry = form.bind(Map("test.entries[0]" -> "Foo"))
      unknownEntry.isLeft mustBe true
      unknownEntry.left.get.head.message mustBe "error.enum"

      val nonOtherEntries = form.bind(Map("test.entries[0]" -> "TestA", "test.entries[1]" -> "TestB"))
      nonOtherEntries.isRight mustBe true
      nonOtherEntries.right.get.size mustBe 2
      nonOtherEntries.right.get.contains(TestEnumEntry.TestA) mustBe true
      nonOtherEntries.right.get.contains(TestEnumEntry.TestB) mustBe true

      val otherWithMissingValue = form.bind(Map("test.entries[0]" -> "TestA", "test.entries[1]" -> "Other"))
      otherWithMissingValue.isLeft mustBe true
      otherWithMissingValue.left.get.head.message mustBe "error.enumWithOther.otherValue.empty"

      val otherWithEmptyValue = form.bind(Map("test.entries[0]" -> "TestA", "test.entries[1]" -> "Other", "test.otherValue" -> ""))
      otherWithEmptyValue.isLeft mustBe true
      otherWithEmptyValue.left.get.head.message mustBe "error.enumWithOther.otherValue.empty"

      val otherWithValue = form.bind(Map("test.entries[0]" -> "TestA", "test.entries[1]" -> "Other", "test.otherValue" -> "The value"))
      otherWithValue.isRight mustBe true
      otherWithValue.right.get.size mustBe 2
      otherWithValue.right.get.contains(TestEnumEntry.TestA) mustBe true
      otherWithValue.right.get.contains(TestEnumEntry.Other(Some("The value"))) mustBe true
    }
  }

}
