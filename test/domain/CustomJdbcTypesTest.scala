package domain

import java.sql.Timestamp
import java.time.{OffsetDateTime, ZonedDateTime}
import java.util.UUID

import domain.dao.AbstractDaoTest
import helpers.JavaTime
import slick.jdbc.PostgresProfile.api._

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
      def pk = primaryKey("ENTITY_PK", id)
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

    "work across DST boundaries" in new DatabaseFixture {
      val dt1 = ZonedDateTime.of(2018, 10, 28, 0, 30, 0, 0, JavaTime.timeZone)
      val dt2 = dt1.plusHours(1).toOffsetDateTime
      val dt3 = dt1.plusHours(2).toOffsetDateTime

      dt2.toString mustBe "2018-10-28T01:30+01:00"
      dt3.toString mustBe "2018-10-28T01:30Z"

      val entity2 = Entity(UUID.randomUUID(), dt2)
      val entity3 = Entity(UUID.randomUUID(), dt3)

      db.run(table += entity2).futureValue mustBe 1
      db.run(table += entity3).futureValue mustBe 1

      db.run(table.filter(_.id === entity2.id).map(_.dtButItsATimestamp).result.head).futureValue.toString mustBe "2018-10-28 00:30:00.0"
      db.run(table.filter(_.id === entity3.id).map(_.dtButItsATimestamp).result.head).futureValue.toString mustBe "2018-10-28 01:30:00.0"

      db.run(table.filter(_.id === entity2.id).result.head).futureValue mustBe entity2
      db.run(table.filter(_.id === entity3.id).result.head).futureValue mustBe entity3
    }
  }

}
