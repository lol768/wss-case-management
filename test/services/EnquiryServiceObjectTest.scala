package services

import domain._
import helpers.JavaTime
import org.scalatestplus.play.PlaySpec

class EnquiryServiceObjectTest extends PlaySpec {

  import services.EnquiryService._

  val enquiryToday = Enquiry(universityID = null, subject = "Enquiry", team = null)
  private val enquiryLastWeek = enquiryToday.copy(version = JavaTime.offsetDateTime.minusWeeks(1))
  private val enquiryNextWeek = enquiryToday.copy(version = JavaTime.offsetDateTime.plusWeeks(1))

  val messageTomorrow = MessageData("hello", MessageSender.Client, JavaTime.offsetDateTime.plusDays(1), None)
  val messageLastWeek = MessageData("hello", MessageSender.Client, JavaTime.offsetDateTime.minusWeeks(1), None)

  "lastModified" should {

    "always use enquiry if no messages" in {
      lastModified(EnquiryRender(enquiryLastWeek, Nil)) mustBe enquiryLastWeek.version
    }

    "use enquiry date if newer" in {
      lastModified(EnquiryRender(enquiryToday, Seq((messageLastWeek, Nil), (messageLastWeek, Nil)))) mustBe enquiryToday.version
    }

    "use most recent message if newer" in {
      lastModified(EnquiryRender(enquiryLastWeek, Seq((messageLastWeek, Nil), (messageTomorrow, Nil)))) mustBe messageTomorrow.created
    }

  }

  "sortByRecent" should {
    "sort descending" in {
      val item1 = EnquiryRender(enquiryLastWeek, Nil)
      val item2 = EnquiryRender(enquiryToday, Seq((messageLastWeek, Nil)))
      val item3 = EnquiryRender(enquiryLastWeek, Seq((messageLastWeek, Nil), (messageTomorrow, Nil)))

      sortByRecent(Seq(
        item3, item1, item2
      )) mustBe Seq(
        item3, item2, item1
      )
    }
  }

}
