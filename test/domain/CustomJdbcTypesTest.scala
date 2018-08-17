package domain

import java.sql.{PreparedStatement, Timestamp}
import java.time.{OffsetDateTime, ZoneOffset, ZonedDateTime}
import java.util.UUID

import slick.jdbc.PostgresProfile.api._
import domain.dao.AbstractDaoTest
import helpers.JavaTime

class CustomJdbcTypesTest extends AbstractDaoTest {

  import CustomJdbcTypes._

  trait DatabaseFixture {
    def db: Database = dbConfig.db

    case class Entity(id: UUID, dt: OffsetDateTime)

    class EntityTable(tag: Tag) extends Table[Entity](tag, "ENTITY") {
      def id = column[UUID]("id")
      def dt = column[OffsetDateTime]("DT")
      def dtButItsATimestamp = column[Timestamp]("DT")

      def * = (id, dt).mapTo[Entity]
    }

    val table = TableQuery[EntityTable]

    execWithCommit(table.schema.create)
  }

  "OffsetDateTime mapper" should {
    "correctly map to UTC" in new DatabaseFixture {
      val dt = ZonedDateTime.of(2018, 8, 17, 10, 44, 43, 182000000, JavaTime.timeZone).toOffsetDateTime

      val entity = Entity(UUID.randomUUID(), dt)

      execWithCommit(table += entity) mustBe 1

      val inserted = db.run(table.filter(_.id === entity.id).result.head).futureValue
      inserted.id mustBe entity.id
      inserted.dt mustBe dt

      val ts = execWithCommit(table.filter(_.id === entity.id).map(_.dtButItsATimestamp).result.head)
      ts.toString mustBe "2018-08-17 09:44:43.182" // Converted 10:44am BST to UTC

      execWithCommit(table.filter(_.dt === entity.dt).result.headOption) mustBe 'defined
    }
  }

}
