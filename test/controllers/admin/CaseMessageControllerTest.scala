package controllers.admin

import java.time.OffsetDateTime

import domain._
import domain.dao.AbstractDaoTest
import org.scalatestplus.play._
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._
import services.CaseService

import scala.concurrent.Future

class CaseMessageControllerTest extends AbstractDaoTest {

  val controller = get[CaseMessageController]
  val caseService = get[CaseService]

  "messages" should {
    "return good JSON" in {
      val now = OffsetDateTime.parse("2018-06-01T11:00+01:00")
      val c = Fixtures.cases.newStoredCase().asCase
      val uniId = Fixtures.users.studentCaseClient.universityId.get
      val messages = CaseMessages(Seq(
        MessageRender(Fixtures.messages.messageDataFromClient(uniId, now), Nil)
      ))
      val result = Future.successful(controller.messagesResult(c, uniId, messages))
      (contentAsJson(result)\"data"\"lastMessage").as[String] mustBe "2018-06-01T11:00:00.000+01"
    }
  }

}
