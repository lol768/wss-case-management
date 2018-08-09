package domain.dao

import java.util.UUID

import domain.{Message, MessageOwner}
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import domain.CustomJdbcTypes._
import slick.lifted.QueryBase

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[MessageDaoImpl])
trait MessageDao {
  def insert(message: Message): DBIOAction[Message, NoStream, Effect.Write]

  def getByOwner(ownerId: UUID, ownerType: MessageOwner): Query[Message.Messages, Message, Seq]
}

@Singleton
class MessageDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends MessageDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(message: Message): DBIOAction[Message, NoStream, Effect.Write] =
    Message.messages += message

  override def getByOwner(ownerId: UUID, ownerType: MessageOwner): Query[Message.Messages, Message, Seq] =
    Message.messages.table
      .filter(_.ownerId === ownerId)
      .filter(_.ownerType === ownerType)
}
