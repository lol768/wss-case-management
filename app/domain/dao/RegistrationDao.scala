package domain.dao

import java.time.ZonedDateTime
import java.util.UUID

import com.google.inject.{ImplementedBy, Inject}
import domain.CustomJdbcTypes._
import domain.{Registrations, Team, Teams}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json}
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

object RegistrationDao {

  case class Registration(
    id: String,
    universityId: UniversityID,
    updatedDate: ZonedDateTime,
    team: Team,
    data: JsValue
  )

  class Registrations(tag: Tag) extends Table[Registration](tag, "user_registration") {
    def id = column[String]("id")
    def universityId = column[UniversityID]("university_id")
    def updatedDate = column[ZonedDateTime]("updated_date")
    def team = column[Team]("team_id")
    def data = column[JsValue]("data")

    def * = (id, universityId, updatedDate, team, data) <> (Registration.tupled, Registration.unapply)
  }

  val registrations = TableQuery[Registrations]

}

@ImplementedBy(classOf[RegistrationDaoImpl])
trait RegistrationDao {

  def save(studentSupportRegistration: Registrations.StudentSupport): Future[String]

  def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]]

}

class RegistrationDaoImpl @Inject()(
  @NamedDatabase("default") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit executionContext: ExecutionContext) extends RegistrationDao with HasDatabaseConfigProvider[JdbcProfile] {

  import RegistrationDao._
  import dbConfig.profile.api._

  override def save(studentSupportRegistration: Registrations.StudentSupport): Future[String] = {
    val id = UUID.randomUUID().toString
    dbConfig.db.run[Int]((registrations += Registration(
      id,
      studentSupportRegistration.universityID,
      studentSupportRegistration.updatedDate,
      Teams.StudentSupport,
      Json.toJson(studentSupportRegistration.data)(Registrations.StudentSupportData.formatter)
    )).transactionally).map(_ => id)
  }

  override def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]] = {
    dbConfig.db.run[Option[Registration]](
      registrations.filter(_.team === (Teams.StudentSupport:Team))
        .sortBy(_.updatedDate.desc)
        .result
        .headOption
    ).map(_.map(r => Registrations.StudentSupport(
      r.universityId,
      r.updatedDate,
      r.data.validate[Registrations.StudentSupportData](Registrations.StudentSupportData.formatter).get
    )))
  }

}
