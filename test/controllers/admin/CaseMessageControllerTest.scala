package controllers.admin

import java.time.{OffsetDateTime, ZoneId, ZoneOffset}

import domain._
import domain.dao.AbstractDaoTest
import play.api.test.Helpers._
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class CaseMessageControllerTest extends AbstractDaoTest {

  val controller = get[CaseMessageController]

  "messages" should {
    "return good JSON" in {
      val now = OffsetDateTime.parse("2018-06-01T11:00+01:00")
      val c = Fixtures.cases.newStoredCase().asCase
      val uniId = Fixtures.users.studentCaseClient.universityId.get
      val messages = CaseMessages(Seq(
        MessageRender(Fixtures.messages.messageDataFromClient(uniId, now), Nil)
      ))
      val result = Future.successful(controller.messagesResult(c, uniId, messages))

      val expected = JavaTime.timeZone.getId match {
        case "Europe/London" => "2018-06-01T11:00:00.000+01"
        case "Etc/UTC" | "UTC" | "Z" => "2018-06-01T10:00:00.000Z"
      }

      (contentAsJson(result) \ "data" \ "lastMessage").as[String] mustBe expected
    }
  }

}
