package services

import domain.{Enquiry, Fixtures, MessageData, MessageSender}
import helpers.JavaTime
import org.scalatest.FunSuite
import org.scalatestplus.play.PlaySpec

class EnquiryServiceTest extends PlaySpec {

  import services.EnquiryService._

  type Item = (Enquiry, Seq[MessageData])

  val enquiryToday = Enquiry(universityID = null, subject = null, team = null)
  val enquiryLastWeek = enquiryToday.copy(version = JavaTime.offsetDateTime.minusWeeks(1))
  val enquiryNextWeek = enquiryToday.copy(version = JavaTime.offsetDateTime.plusWeeks(1))

  val messageTomorrow = MessageData("hello", MessageSender.Client, JavaTime.offsetDateTime.plusDays(1))
  val messageLastWeek = MessageData("hello", MessageSender.Client, JavaTime.offsetDateTime.minusWeeks(1))

  "lastModified" should {

    "always use enquiry if no messages" in {
      lastModified((enquiryLastWeek, Nil)) mustBe enquiryLastWeek.version
    }

    "use enquiry date if newer" in {
      lastModified((enquiryToday, Seq(messageLastWeek, messageLastWeek))) mustBe enquiryToday.version
    }

    "use most recent message if newer" in {
      lastModified((enquiryLastWeek, Seq(messageLastWeek, messageTomorrow))) mustBe messageTomorrow.created
    }

  }

  "sortByRecent" should {
    "sort descending" in {
      val item1: Item = (enquiryLastWeek, Nil)
      val item2: Item = (enquiryToday, Seq(messageLastWeek))
      val item3: Item = (enquiryLastWeek, Seq(messageLastWeek, messageTomorrow))

      sortByRecent(Seq(
        item3, item1, item2
      )) mustBe Seq(
        item3, item2, item1
      )
    }
  }

}
