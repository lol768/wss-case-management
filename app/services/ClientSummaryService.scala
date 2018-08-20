package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.{ClientSummaryDao, DaoRunner}
import domain.{ClientSummary, ClientSummaryData}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientSummaryServiceImpl])
trait ClientSummaryService {
  def save(universityID: UniversityID, data: ClientSummaryData)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def update(universityID: UniversityID, data: ClientSummaryData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def get(universityID: UniversityID): Future[ServiceResult[Option[ClientSummary]]]
}

@Singleton
class ClientSummaryServiceImpl @Inject()(
  auditService: AuditService,
  dao: ClientSummaryDao,
  daoRunner: DaoRunner
)(implicit executionContext: ExecutionContext) extends ClientSummaryService {

  override def save(universityID: UniversityID, data: ClientSummaryData)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]] =
    auditService.audit(
      "SaveClientSummary",
      universityID.string,
      "ClientSummary",
      Json.toJson(data)(ClientSummaryData.formatter)
    ) {
      daoRunner.run(dao.insert(universityID, data)).map(_.parsed).map(Right.apply)
    }

  override def update(universityID: UniversityID, data: ClientSummaryData, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]] =
    auditService.audit(
      "UpdateClientSummary",
      universityID.string,
      "ClientSummary",
      Json.toJson(data)(ClientSummaryData.formatter)
    ) {
      daoRunner.run(dao.update(universityID, data, version)).map(_.parsed).map(Right.apply)
    }

  override def get(universityID: UniversityID): Future[ServiceResult[Option[ClientSummary]]] =
    daoRunner.run(dao.get(universityID)).map(_.map(_.parsed)).map(Right.apply)

}
