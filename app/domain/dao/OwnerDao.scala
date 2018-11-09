package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import ExtendedPostgresProfile.api._
import services.AuditLogContext

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[OwnerDaoImpl])
trait OwnerDao {
  def insert(owners: Set[Owner])(implicit ac: AuditLogContext): DBIO[Seq[Owner]]
  def update(owner: Owner, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Owner]
  def delete(owners: Set[Owner])(implicit ac: AuditLogContext): DBIO[Done]
  def findEnquiryOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq]
  def findCaseOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq]
  def findAppointmentOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq]
  def getCaseOwnerHistory(id: UUID): DBIO[Seq[OwnerVersion]]
}

@Singleton
class OwnerDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends OwnerDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(owners: Set[Owner])(implicit ac: AuditLogContext): DBIO[Seq[Owner]] =
    Owner.owners ++= owners.toSeq

  override def update(owner: Owner, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[Owner] =
    Owner.owners.update(owner.atVersion(version))

  override def delete(owners: Set[Owner])(implicit ac: AuditLogContext): DBIO[Done] =
    Owner.owners.deleteAll(owners.toSeq)

  override def findCaseOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq] =
    Owner.owners.table
      .filter(o => o.entityId.inSet(ids) && o.entityType === (Owner.EntityType.Case:Owner.EntityType))

  override def findEnquiryOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq] =
    Owner.owners.table
      .filter(o => o.entityId.inSet(ids) && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType))

  override def findAppointmentOwnersQuery(ids: Set[UUID]): Query[Owner.Owners, Owner, Seq] =
    Owner.owners.table
      .filter(o => o.entityId.inSet(ids) && o.entityType === (Owner.EntityType.Appointment:Owner.EntityType))

  override def getCaseOwnerHistory(id: UUID): DBIO[Seq[OwnerVersion]] =
    Owner.owners.versionsTable
      .filter(o => o.entityId === id && o.entityType === (Owner.EntityType.Case:Owner.EntityType))
      .result

}
