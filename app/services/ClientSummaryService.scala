package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.ClientSummaryDao.StoredClientSummary
import domain.dao.ClientSummaryDao.StoredClientSummary.StoredReasonableAdjustment
import domain.dao.{ClientSummaryDao, DaoRunner}
import domain.{ClientSummary, ClientSummarySave, ReasonableAdjustment}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID
import domain.ExtendedPostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientSummaryServiceImpl])
trait ClientSummaryService {
  def save(universityID: UniversityID, summary: ClientSummarySave)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def update(universityID: UniversityID, summary: ClientSummarySave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]]
  def getByAlternativeEmailAddress(email: String)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]]
}

@Singleton
class ClientSummaryServiceImpl @Inject()(
  auditService: AuditService,
  dao: ClientSummaryDao,
  daoRunner: DaoRunner
)(implicit executionContext: ExecutionContext) extends ClientSummaryService {

  private def toStored(universityID: UniversityID, summary: ClientSummarySave): StoredClientSummary = StoredClientSummary(
    universityID = universityID,
    highMentalHealthRisk = summary.highMentalHealthRisk,
    notes = summary.notes,
    alternativeContactNumber = summary.alternativeContactNumber,
    alternativeEmailAddress = summary.alternativeEmailAddress,
    riskStatus = summary.riskStatus
  )

  private def toStored(universityID: UniversityID, reasonableAdjustment: ReasonableAdjustment): StoredReasonableAdjustment = StoredReasonableAdjustment(
    universityID = universityID,
    reasonableAdjustment = reasonableAdjustment
  )

  override def save(universityID: UniversityID, summary: ClientSummarySave)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]] =
    auditService.audit(
      'SaveClientSummary,
      universityID.string,
      'ClientSummary,
      Json.toJson(summary)(ClientSummarySave.formatter)
    ) {
      daoRunner.run(for {
        inserted <- dao.insert(toStored(universityID, summary))
        reasonableAdjustments <- dao.insertReasonableAdjustments(summary.reasonableAdjustments.map(r => toStored(universityID, r)))
      } yield (inserted, reasonableAdjustments)).map { case (inserted, reasonableAdjustments) =>
        Right(inserted.asClientSummary(reasonableAdjustments.map(_.reasonableAdjustment).toSet))
      }
    }

  override def update(universityID: UniversityID, summary: ClientSummarySave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]] =
    auditService.audit(
      'UpdateClientSummary,
      universityID.string,
      'ClientSummary,
      Json.toJson(summary)(ClientSummarySave.formatter)
    ) {
      daoRunner.run(for {
        updated <- dao.update(toStored(universityID, summary), version)
        _ <- updateDifferencesDBIO[StoredReasonableAdjustment, ReasonableAdjustment](
          summary.reasonableAdjustments,
          dao.getReasonableAdjustmentsQuery(universityID),
          _.reasonableAdjustment,
          r => toStored(universityID, r),
          dao.insertReasonableAdjustment,
          dao.deleteReasonableAdjustment
        )
        updatedAdjustments <- dao.getReasonableAdjustmentsQuery(universityID).result
      } yield {
        Right(updated.asClientSummary(updatedAdjustments.map(_.reasonableAdjustment).toSet))
      })
    }

  override def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]] =
    withReasonableAdjustments(dao.get(universityID))

  override def getByAlternativeEmailAddress(email: String)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]] =
    withReasonableAdjustments(dao.getByAlternativeEmailAddress(email))

  private def withReasonableAdjustments(result: DBIO[Option[StoredClientSummary]])(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]] = {
    daoRunner.run(for {
      summaryOption <- result
      adjustments <- summaryOption match {
        case Some(summary) => dao.getReasonableAdjustmentsQuery(summary.universityID).result
        case None => DBIO.successful(Nil)
      }
    } yield {
      summaryOption.map(_.asClientSummary(adjustments.map(_.reasonableAdjustment).toSet))
    }).map(Right.apply)
  }
}
