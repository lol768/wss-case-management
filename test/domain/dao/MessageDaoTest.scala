package domain.dao

import java.util.UUID

import domain.Message.Messages
import domain.{Fixtures, Message}
import warwick.sso.UniversityID
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._

import scala.concurrent.Future

class MessageDaoTest extends AbstractDaoTest {

  private val dao = get[MessageDao]

  "MessageDao" should {
    "save clients" in {
      val client1 = UniversityID("01234567")
      val client3 = UniversityID("9999999")
      val message = Fixtures.messages.newEnquiryMessage(UUID.randomUUID(), client1)
      def filterMessage(client: UniversityID): Query[Messages, Message, Seq] = {
        Message.messages.table.filter(_.client === client).sortBy(_.created)
      }

      exec(for {
        _ <- dao.insert(message)
        client1Messages <- filterMessage(client1).result
        client3Messages <- filterMessage(client3).result
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
