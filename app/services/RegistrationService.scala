package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.{DaoRunner, RegistrationDao}
import domain.{Registration, RegistrationData, RegistrationDataHistory}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegistrationServiceImpl])
trait RegistrationService {

  def save(universityID: UniversityID, data: domain.RegistrationData)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

  def update(universityID: UniversityID, data: domain.RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

  def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[domain.Registration]]]

  def getHistory(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[RegistrationDataHistory]]

}

@Singleton
class RegistrationServiceImpl @Inject()(
  auditService: AuditService,
  dao: RegistrationDao,
  daoRunner: DaoRunner,
  notificationService: NotificationService
)(implicit executionContext: ExecutionContext) extends RegistrationService {

  override def save(universityID: UniversityID, data: RegistrationData)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    auditService.audit(
      'SaveRegistration,
      universityID.string,
      'Registration,
      Json.toJson(data)(RegistrationData.formatter)
    ) {
      daoRunner.run(dao.insert(universityID, data)).map(_.parsed).map(Right.apply)
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      registration => notificationService.newRegistration(universityID).map(_.right.map(_ => registration))
    ))

  override def update(universityID: UniversityID, data: RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    auditService.audit(
      'UpdateRegistration,
      universityID.string,
      'Registration,
      Json.toJson(data)(RegistrationData.formatter)
    ) {
      daoRunner.run(dao.update(universityID, data, version)).map(_.parsed).map(Right.apply)
    }

  override def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[domain.Registration]]] =
    daoRunner.run(dao.get(universityID)).map(_.map(_.parsed)).map(Right.apply)

  override def getHistory(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[RegistrationDataHistory]] = {
    daoRunner.run(dao.getHistory(universityID)).map(_.map { case (jsValue, ts) => (
      jsValue.validate[domain.RegistrationData](domain.RegistrationData.formatter).get,
      ts
    )}).map(RegistrationDataHistory.apply).map(Right.apply)
  }

}
