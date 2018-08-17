package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.{DaoRunner, RegistrationDao}
import domain.{Registration, RegistrationData}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegistrationServiceImpl])
trait RegistrationService {

  def save(universityID: UniversityID, data: domain.RegistrationData)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

  def update(universityID: UniversityID, data: domain.RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

  def get(universityID: UniversityID): Future[ServiceResult[Option[domain.Registration]]]

}

@Singleton
class RegistrationServiceImpl @Inject()(
  auditService: AuditService,
  dao: RegistrationDao,
  daoRunner: DaoRunner
)(implicit executionContext: ExecutionContext) extends RegistrationService {

  override def save(universityID: UniversityID, data: RegistrationData)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    auditService.audit(
      "SaveRegistration",
      universityID.string,
      "Registration",
      Json.toJson(data)(RegistrationData.formatter)
    ) {
      daoRunner.run(dao.insert(universityID, data)).map(_.parsed).map(Right.apply)
    }

  override def update(universityID: UniversityID, data: RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    auditService.audit(
      "UpdateRegistration",
      universityID.string,
      "Registration",
      Json.toJson(data)(RegistrationData.formatter)
    ) {
      daoRunner.run(dao.update(universityID, data, version)).map(_.parsed).map(Right.apply)
    }

  override def get(universityID: UniversityID): Future[ServiceResult[Option[domain.Registration]]] =
    daoRunner.run(dao.get(universityID)).map(_.map(_.parsed)).map(Right.apply)

}
