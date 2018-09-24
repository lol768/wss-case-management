package domain.dao

import java.util.UUID

import domain.Fixtures
import warwick.sso.UniversityID
import scala.concurrent.Future

class MessageDaoTest extends AbstractDaoTest {

  val dao = get[MessageDao]

  import profile.api._

  "MessageDao" should {
    "save clients" in {
      val client1 = UniversityID("01234567")
      val client3 = UniversityID("9999999")
      val message = Fixtures.messages.newEnquiryMessage(UUID.randomUUID(), client1)

      exec(for {
        _ <- dao.insert(message)
        client1Messages <- dao.findByClientQuery(client1).result
        client3Messages <- dao.findByClientQuery(client3).result
        _ <- DBIO.from(Future {
          client1Messages.length mustBe 1
          client1Messages.head.id mustBe message.id
          client1Messages.head.text mustBe "Hello"

          client3Messages.length mustBe 0
        })
      } yield Nil)
    }
  }

}
