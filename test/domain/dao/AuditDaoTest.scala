package domain.dao

import domain._
import helpers.OneAppPerSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import warwick.sso.Usercode

class AuditDaoTest extends PlaySpec with MockitoSugar with OneAppPerSuite with ScalaFutures {

  private val dao = get[AuditDao]

  "AuditDao" should {
    "save and fetch audit events" in {
      val event = AuditEvent(
        operation = "create",
        usercode = Some(Usercode("cuscav")),
        data = Json.obj("yes" -> true),
        targetId = "steven",
        targetType = "thing"
      )

      val savedEvent = dao.insert(event).futureValue
      savedEvent mustBe event.copy(id = savedEvent.id)

      dao.getById(savedEvent.id.get).futureValue mustBe Some(savedEvent)
    }

  }
}
