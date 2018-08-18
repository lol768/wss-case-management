package domain.dao

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain._
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import slick.jdbc.PostgresProfile.api._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ClientSummaryDaoImpl])
trait ClientSummaryDao {

}

object ClientSummaryDao {
  case class PersistedClientSummary(
    universityID: UniversityID,
    data: JsValue,
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[PersistedClientSummary] {
    override def atVersion(at: OffsetDateTime): PersistedClientSummary = copy(version = at)

    override def storedVersion[B <: StoredVersion[PersistedClientSummary]](operation: DatabaseOperation, timestamp: OffsetDateTime): B =
      PersistedClientSummaryVersion(
        universityID,
        data,
        version,
        operation,
        timestamp
      ).asInstanceOf[B]

    def parsed = ClientSummary(
      universityID = universityID,
      updatedDate = version,
      data = data.validate[ClientSummaryData](ClientSummaryData.formatter).get
    )
  }

  case class PersistedClientSummaryVersion(
    universityId: UniversityID,
    data: JsValue,
    version: OffsetDateTime = JavaTime.offsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime
  ) extends StoredVersion[PersistedClientSummary]
}

@Singleton
class ClientSummaryDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends ClientSummaryDao with HasDatabaseConfigProvider[JdbcProfile] {

}

