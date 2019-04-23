package services

import domain.AuditEvent._
import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.{DaoRunner, RegistrationDao}
import domain.{Registration, RegistrationData, RegistrationDataHistory}
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID
import warwick.core.helpers.ServiceResults.Implicits._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegistrationServiceImpl])
trait RegistrationService {

  def invite(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

  def register(universityID: UniversityID, data: domain.RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[domain.Registration]]

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

  override def invite(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    auditService.audit(
      Operation.Registration.SendInvite,
      universityID.string,
      Target.Registration,
      Json.obj()
    ) {
      get(universityID).successFlatMapTo { existing =>
        val action = existing.map(r => dao.update(universityID, r.data, JavaTime.offsetDateTime, r.updatedDate)).getOrElse(dao.invite(universityID))
        daoRunner.run(action).map(_.parsed).map(Right.apply)
      }
    }.flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      registration => notificationService.registrationInvite(universityID).map(_.right.map(_ => registration))
    ))

  override def register(universityID: UniversityID, data: RegistrationData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Registration]] =
    get(universityID).successFlatMapTo(_.map(existing =>
      if (existing.data.isEmpty) {
        auditService.audit(
          Operation.Registration.Save,
          universityID.string,
          Target.Registration,
          Json.toJson(data)(RegistrationData.formatter)
        ) {
          daoRunner.run(dao.update(universityID, Some(data), existing.lastInvited, version)).map(_.parsed).map(Right.apply)
        }.flatMap(_.fold(
          errors => Future.successful(Left(errors)),
          registration => notificationService.newRegistration(universityID).map(_.right.map(_ => registration))
        ))
      } else {
        auditService.audit(
          Operation.Registration.Update,
          universityID.string,
          Target.Registration,
          Json.toJson(data)(RegistrationData.formatter)
        ) {
          daoRunner.run(dao.update(universityID, Some(data), existing.lastInvited, version)).map(_.parsed).map(Right.apply)
        }
      }
    ).getOrElse(throw new IllegalArgumentException(s"${universityID.string} attempted to register without an invite"))
  )


  override def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[domain.Registration]]] =
    daoRunner.run(dao.get(universityID)).map(_.map(_.parsed)).map(Right.apply)

  override def getHistory(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[RegistrationDataHistory]] = {
    daoRunner.run(dao.getHistory(universityID)).map(_.map { case (jsValue, ts) => (
      jsValue.validate[domain.RegistrationData](domain.RegistrationData.formatter).get,
      ts
    )}).map(RegistrationDataHistory.apply).map(Right.apply)
  }

}
