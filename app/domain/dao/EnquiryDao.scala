package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import domain._
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID
import domain.CustomJdbcTypes._

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[EnquiryDaoImpl])
trait EnquiryDao {
  def insert(enquiry: Enquiry): DBIO[Enquiry]
  def update(enquiry: Enquiry, version: OffsetDateTime): DBIO[Enquiry]
  def insert(owner: EnquiryOwner): DBIO[EnquiryOwner]
  def delete(owner: EnquiryOwner): DBIO[Done]
  def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOpenQuery(team: Team): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOpenQuery(owner: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOwnersQuery(ids: Set[UUID]): Query[EnquiryOwner.EnquiryOwners, EnquiryOwner, Seq]
}

@Singleton
class EnquiryDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  messageDao: MessageDao
)(implicit ec: ExecutionContext)
  extends EnquiryDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(enquiry: Enquiry): DBIO[Enquiry] =
    Enquiry.enquiries += enquiry

  override def update(enquiry: Enquiry, version: OffsetDateTime): DBIO[Enquiry] =
    Enquiry.enquiries.update(enquiry.copy(version = version))

  override def insert(owner: EnquiryOwner): DBIO[EnquiryOwner] =
    EnquiryOwner.enquiryOwners += owner

  override def delete(owner: EnquiryOwner): DBIO[Done] =
    EnquiryOwner.enquiryOwners.delete(owner)

  def getById(id: UUID): DBIO[Enquiry] = Enquiry.enquiries.table.filter(_.id === id).take(1).result.head

  def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.id === id)

  def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.universityId === client)

  override def findOpenQuery(team: Team): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table
      .filter(e => e.isOpen && e.team === team)

  override def findOpenQuery(owner: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table
      .join(EnquiryOwner.enquiryOwners.table)
      .on(_.id === _.enquiryId)
      .filter { case (e, o) => e.isOpen && o.universityId === owner }
      .map { case (e, _) => e }

  override def findOwnersQuery(ids: Set[UUID]): Query[EnquiryOwner.EnquiryOwners, EnquiryOwner, Seq] =
    EnquiryOwner.enquiryOwners.table
      .filter(o => o.enquiryId.inSet(ids))

}
