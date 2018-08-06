package services

import com.google.inject.ImplementedBy
import domain.Registrations
import domain.dao.RegistrationDao
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import play.api.libs.json.Json
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegistrationServiceImpl])
trait RegistrationService {

  def save(counsellingRegistration: Registrations.Counselling)(implicit auditLogContext: AuditLogContext): Future[ServiceResult[String]]

  def getCounselling(universityID: UniversityID): Future[Option[Registrations.Counselling]]

  def save(studentSupportRegistration: Registrations.StudentSupport)(implicit auditLogContext: AuditLogContext): Future[ServiceResult[String]]

  def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]]


}

class RegistrationServiceImpl @Inject()(
  dao: RegistrationDao,
  auditService: AuditService
)(implicit executionContext: ExecutionContext) extends RegistrationService {

  override def save(counsellingRegistration: Registrations.Counselling)(implicit auditLogContext: AuditLogContext): Future[ServiceResult[String]] =
    auditService.audit[String](
      "SaveRegistration",
      (id: String) => id,
      "Registrations.Counselling",
      Json.obj(
        "universityId" -> counsellingRegistration.universityID.string
      )
    ) {
      dao.save(counsellingRegistration).map(Right.apply)
    }

  override def getCounselling(universityID: UniversityID): Future[Option[Registrations.Counselling]] =
    dao.getCounselling(universityID)

  override def save(studentSupportRegistration: Registrations.StudentSupport)(implicit auditLogContext: AuditLogContext): Future[ServiceResult[String]] =
    auditService.audit[String](
      "SaveRegistration",
      (id: String) => id,
      "Registrations.StudentSupport",
      Json.obj(
        "universityId" -> studentSupportRegistration.universityID.string
      )
    ) {
      dao.save(studentSupportRegistration).map(Right.apply)
    }

  override def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]] =
    dao.getStudentSupport(universityID)

}
