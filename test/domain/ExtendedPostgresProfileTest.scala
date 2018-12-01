package domain

import java.util.UUID

import domain.ExtendedPostgresProfile.api._
import domain.dao.AbstractDaoTest
import helpers.DataFixture

class ExtendedPostgresProfileTest extends AbstractDaoTest {

  case class Entity(id: UUID, string: String)

  class EntityTable(tag: Tag) extends Table[Entity](tag, "ENTITY") {
    def id = column[UUID]("id")
    def string = column[String]("string")
    def searchableString = toTsVector(string, Some("english"))

    def * = (id, string).mapTo[Entity]
    def pk = primaryKey("ENTITY_PK", id)
  }

  val table = TableQuery[EntityTable]

  class DatabaseFixture extends DataFixture[Unit] {
    override def setup(): Unit = execWithCommit(table.schema.create)
    override def teardown(): Unit = execWithCommit(table.schema.drop)
  }

  "ExtendedPostgresProfile" should {
    "strip null bytes out before they reach the database" in withData(new DatabaseFixture) { _ =>
      val e = Entity(UUID.randomUUID(), "valid string with null byte\u0000 in the middle")
      execWithCommit(table += e)

      val e2 = exec(table.filter(_.id === e.id).result.head)
      e2.id mustBe e.id
      e2.string mustBe "valid string with null byte in the middle"
    }

    "search with multiple word prefixes" in withData(new DatabaseFixture) { _ =>
      val e = Entity(UUID.randomUUID(), "hey, here's my magic string")
      execWithCommit(table += e)

      exec(table.filter(_.searchableString @@ prefixTsQuery("magic hey str".bind)).length.result) mustBe 1
    }
  }

}
