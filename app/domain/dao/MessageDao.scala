package domain.dao

import com.google.inject.ImplementedBy
import domain.ExtendedPostgresProfile.api._
import domain.{ExtendedPostgresProfile, Message}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[MessageDaoImpl])
trait MessageDao {
  def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message]
}

@Singleton
class MessageDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends MessageDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  override def insert(message: Message)(implicit ac: AuditLogContext): DBIO[Message] =
    Message.messages += message
}
