package domain.dao

import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[OwnerDaoImpl])
trait OwnerDao {
  def insert(owner: Owner): DBIO[Owner]
  def delete(owner: Owner): DBIO[Done]
  def findEnquiryOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq]
  def findCaseOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq]
}

@Singleton
class OwnerDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends OwnerDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(owner: Owner): DBIO[Owner] =
    Owner.owners += owner

  override def delete(owner: Owner): DBIO[Done] =
    Owner.owners.delete(owner)

  override def findCaseOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq] =
    Owner.owners.table
      .filter(o => o.entityId.inSet(ids) && o.entityType === (Owner.EntityType.Case:Owner.EntityType))

  override def findEnquiryOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq] =
    Owner.owners.table
      .filter(o => o.entityId.inSet(ids) && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType))

}
