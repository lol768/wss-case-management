package services

import java.util.UUID

import domain._
import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime

class EnquiryServiceObjectTest extends PlaySpec {

  import services.EnquiryService._

  private val client = Fixtures.users.studentNewVisitor
  val enquiryToday = Enquiry(id = UUID.randomUUID(), key = IssueKey(IssueKeyType.Enquiry, 1234), client = Client(client.universityId.get, client.name.full, JavaTime.offsetDateTime), subject = "Enquiry", team = null, state = null, lastUpdated = JavaTime.offsetDateTime, created = null)
  private val enquiryLastWeek = enquiryToday.copy(lastUpdated = JavaTime.offsetDateTime.minusWeeks(1))

  val messageTomorrow = MessageData("hello", MessageSender.Client, client.universityId.get, JavaTime.offsetDateTime.plusDays(1), None, None)
  val messageLastWeek = MessageData("hello", MessageSender.Client, client.universityId.get, JavaTime.offsetDateTime.minusWeeks(1), None, None)

  "lastModified" should {

    "always use enquiry if no messages" in {
      lastModified(EnquiryRender(enquiryLastWeek, Nil, Nil)) mustBe enquiryLastWeek.lastUpdated
    }

    "use enquiry date if newer" in {
      lastModified(EnquiryRender(enquiryToday, Seq(MessageRender(messageLastWeek, Nil), MessageRender(messageLastWeek, Nil)), Nil)) mustBe enquiryToday.lastUpdated
    }

    "use most recent message if newer" in {
      lastModified(EnquiryRender(enquiryLastWeek, Seq(MessageRender(messageLastWeek, Nil), MessageRender(messageTomorrow, Nil)), Nil)) mustBe messageTomorrow.created
    }

  }

  "sortByRecent" should {
    "sort descending" in {
      val item1 = EnquiryRender(enquiryLastWeek, Nil, Nil)
      val item2 = EnquiryRender(enquiryToday, Seq(MessageRender(messageLastWeek, Nil)), Nil)
      val item3 = EnquiryRender(enquiryLastWeek, Seq(MessageRender(messageLastWeek, Nil), MessageRender(messageTomorrow, Nil)), Nil)

      sortByRecent(Seq(
        item3, item1, item2
      )) mustBe Seq(
        item3, item2, item1
      )
    }
  }

}
