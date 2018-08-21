package domain.dao

import java.util.UUID

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
  def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq]
  def findOpenQuery: Query[Enquiry.Enquiries, Enquiry, Seq]
}

@Singleton
class EnquiryDaoImpl @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  messageDao: MessageDao
)(implicit ec: ExecutionContext)
  extends EnquiryDao with HasDatabaseConfigProvider[JdbcProfile] {

  override def insert(enquiry: Enquiry): DBIO[Enquiry] =
    Enquiry.enquiries += enquiry

  def getById(id: UUID): DBIO[Enquiry] = Enquiry.enquiries.table.filter(_.id === id).take(1).result.head

  def findByIDQuery(id: UUID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.id === id)

  def findByClientQuery(client: UniversityID): Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table.filter(_.universityId === client).sortBy(_.version.reverse)

  def findOpenQuery: Query[Enquiry.Enquiries, Enquiry, Seq] =
    Enquiry.enquiries.table
      .filter(_.isOpen)
      .sortBy(_.version.reverse)

}
