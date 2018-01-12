package domain.dao

import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import domain.AuditEvent
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AuditDaoImpl])
trait AuditDao {
  def insert(event: AuditEvent): Future[AuditEvent]
}

@Singleton
class AuditDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends AuditDao with HasDatabaseConfigProvider[JdbcProfile] {
  import dbConfig.profile.api._
  import AuditEvent._

  override def insert(event: AuditEvent): Future[AuditEvent] = {
    val eventWithId = event.copy(id = Some(UUID.randomUUID()))

    dbConfig.db.run((auditEvents += eventWithId).transactionally).map {
      _ => eventWithId
    }
  }
}
