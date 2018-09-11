package domain.dao

import java.util.UUID

import domain._
import play.api.libs.json.Json
import warwick.sso.Usercode

class AuditDaoTest extends AbstractDaoTest {

  private val dao = get[AuditDao]

  "AuditDao" should {
    "save and fetch audit events" in {
      val event = AuditEvent(
        id = UUID.randomUUID(),
        operation = 'create,
        usercode = Some(Usercode("cuscav")),
        data = Json.obj("yes" -> true),
        targetId = "steven",
        targetType = 'thing
      )

      val savedEvent = exec(dao.insert(event))
      savedEvent mustBe event.copy(id = savedEvent.id)

      exec(dao.getById(savedEvent.id)) mustBe Some(savedEvent)
    }
  }
}
