package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[EnquiryDaoImpl])
trait EnquiryDao {
  def insert(enquiry: Enquiry): DBIO[Enquiry]
  def update(enquiry: Enquiry, version: OffsetDateTime): DBIO[Enquiry]
  def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findByIDsQuery(ids: Set[UUID]): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findByKeyQuery(key: IssueKey): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOpenQuery(team: Team): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOpenQuery(owner: Usercode): Query[Enquiry.Enquiries, Enquiry, Seq]
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

  override def getById(id: UUID): DBIO[Enquiry] = Enquiry.enquiries.table.filter(_.id === id).take(1).result.head

  override def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.id === id)

  override def findByIDsQuery(ids: Set[UUID]): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.id.inSet(ids))

  override def findByKeyQuery(key: IssueKey): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.key === key)

  override def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.universityId === client)

  override def findOpenQuery(team: Team): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table
      .filter(e => e.isOpen && e.team === team)

  override def findOpenQuery(owner: Usercode): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table
      .join(Owner.owners.table)
      .on((e, o) => e.id === o.entityId && o.entityType === (Owner.EntityType.Enquiry:Owner.EntityType))
      .filter { case (e, o) => e.isOpen && o.userId === owner }
      .map { case (e, _) => e }

}
