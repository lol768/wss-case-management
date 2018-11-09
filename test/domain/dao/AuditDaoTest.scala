package domain.dao

import java.util.UUID

import domain._
import play.api.libs.json.Json
import slick.dbio.DBIOAction
import warwick.sso.Usercode

import scala.concurrent.Future

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

      val test = for {
        savedEvent <- dao.insert(event)
        fetchedEvent <- dao.getById(savedEvent.id)
        _ <- DBIOAction.from(Future {
          savedEvent mustBe event.copy(id = savedEvent.id)
          fetchedEvent mustBe Some(savedEvent)
        })
      } yield savedEvent

      exec(test)
    }
  }
}
