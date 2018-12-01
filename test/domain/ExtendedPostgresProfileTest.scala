package domain

import java.util.UUID

import com.github.tminglei.slickpg.TsVector
import domain.ExtendedPostgresProfile.api._
import domain.dao.AbstractDaoTest
import helpers.DataFixture

class ExtendedPostgresProfileTest extends AbstractDaoTest {

  case class Entity(id: UUID, string: String, child: Option[UUID] = None)

  class EntityTable(tag: Tag) extends Table[Entity](tag, "entity") {
    def id = column[UUID]("id")
    def string = column[String]("string")
    def searchableString = toTsVector(string, Some("english"))
    def child = column[Option[UUID]]("child_id")

    def * = (id, string, child).mapTo[Entity]
    def pk = primaryKey("entity_pk", id)
    def fk = foreignKey("fk_entity_child", child, table)(_.id.?)
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

    "search optional relationship" in withData(new DatabaseFixture) { _ =>
      val child = Entity(UUID.randomUUID(), "hey, here's my magic string")
      execWithCommit(table += child)

      val parent = Entity(UUID.randomUUID(), "another random string", Some(child.id))
      execWithCommit(table += parent)

      def searchForIDs(search: String): Int =
        exec {
          table
            .joinLeft(table)
            .on(_.child === _.id)
            .filter { case (p, c) =>
              (p.searchableString @+ c.map(_.searchableString).getOrElse("".bind.asColumnOf[TsVector])) @@ prefixTsQuery(search.bind)
            }
            .length.result
        }

      searchForIDs("random magic") mustBe 1
      searchForIDs("random") mustBe 1
      searchForIDs("string") mustBe 2
      searchForIDs("wibble") mustBe 0
    }
  }

}
