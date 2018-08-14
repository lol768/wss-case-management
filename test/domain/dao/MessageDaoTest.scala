package domain.dao

import java.util.UUID

import domain.{Fixtures, Message}
import warwick.sso.UniversityID
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class MessageDaoTest extends AbstractDaoTest {

  val dao = get[MessageDao]

  

  "MessageDao" should {
    "save clients" in {
      val message = Fixtures.messages.newEnquiryMessage(UUID.randomUUID())

      val client1 = UniversityID("01234567")
      val client2 = UniversityID("01234568")
      val client3 = UniversityID("9999999")

      exec(for {
        _ <- dao.insert(message, Seq(client1, client2))
        client1Messages <- dao.getByClient(client1).result
        client3Messages <- dao.getByClient(client3).result
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
