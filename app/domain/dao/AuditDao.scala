package domain.dao

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent
import domain.AuditEvent.AuditEvents
import domain.CustomJdbcTypes._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditDaoImpl])
trait AuditDao {
  def insert(event: AuditEvent): DBIO[AuditEvent]
  def getById(id: UUID): DBIO[Option[AuditEvent]]
  def findByOperationAndUsercodeQuery(operation: Symbol, usercode: Usercode): Query[AuditEvents, AuditEvent, Seq]
}

@Singleton
class AuditDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends AuditDao with HasDatabaseConfigProvider[JdbcProfile] {
  import AuditEvent._

  override def insert(event: AuditEvent): DBIO[AuditEvent] =
    (auditEvents += event).map(_ => event)

  override def getById(id: UUID): DBIO[Option[AuditEvent]] =
    auditEvents.filter(_.id === id).result.headOption

  override def findByOperationAndUsercodeQuery(operation: Symbol, usercode: Usercode): Query[AuditEvents, AuditEvent, Seq] =
    auditEvents.filter { ae => ae.operation === operation && ae.usercode === usercode }
}
