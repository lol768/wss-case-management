package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.dao.ClientSummaryDao.StoredClientSummary
import domain.dao.ClientSummaryDao.StoredClientSummary.StoredReasonableAdjustment
import domain.dao.{ClientSummaryDao, DaoRunner}
import domain._
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID
import domain.ExtendedPostgresProfile.api._
import helpers.{JavaTime, ServiceResults}
import ServiceResults.Implicits._
import domain.dao.ClientDao.StoredClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientSummaryServiceImpl])
trait ClientSummaryService {
  def save(universityID: UniversityID, summary: ClientSummarySave)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def update(universityID: UniversityID, summary: ClientSummarySave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]]
  def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]]
  def getByAlternativeEmailAddress(email: String)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]]
  def findAtRisk(includeMentalHealth: Boolean)(implicit t: TimingContext): Future[ServiceResult[Set[AtRiskClient]]]
}

@Singleton
class ClientSummaryServiceImpl @Inject()(
  auditService: AuditService,
  caseService: CaseService,
  enquiryService: EnquiryService,
  clientService: ClientService,
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
      clientService.getOrAddClients(Set(universityID)).successFlatMapTo(clients =>
        daoRunner.run(for {
          inserted <- dao.insert(toStored(universityID, summary))
          reasonableAdjustments <- dao.insertReasonableAdjustments(summary.reasonableAdjustments.map(r => toStored(universityID, r)))
        } yield (inserted, reasonableAdjustments)).map { case (inserted, reasonableAdjustments) =>
          Right(inserted.asClientSummary(clients.head, reasonableAdjustments.map(_.reasonableAdjustment).toSet))
        }
      )
    }

  override def update(universityID: UniversityID, summary: ClientSummarySave, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientSummary]] =
    auditService.audit(
      'UpdateClientSummary,
      universityID.string,
      'ClientSummary,
      Json.toJson(summary)(ClientSummarySave.formatter)
    ) {
      clientService.getOrAddClients(Set(universityID)).successFlatMapTo(clients =>
        daoRunner.run(for {
          updated <- dao.update(toStored(universityID, summary), version)
          _ <- updateDifferencesDBIO[StoredReasonableAdjustment, ReasonableAdjustment](
            summary.reasonableAdjustments,
            dao.getReasonableAdjustmentsQuery(universityID),
            _.reasonableAdjustment,
            r => toStored(universityID, r),
            dao.insertReasonableAdjustments,
            dao.deleteReasonableAdjustments
          )
          updatedAdjustments <- dao.getReasonableAdjustmentsQuery(universityID).result
        } yield {
          Right(updated.asClientSummary(clients.head, updatedAdjustments.map(_.reasonableAdjustment).toSet))
        })
      )
    }

  override def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]] =
    withReasonableAdjustments(dao.get(universityID)).map(Right.apply)

  override def getByAlternativeEmailAddress(email: String)(implicit t: TimingContext): Future[ServiceResult[Option[ClientSummary]]] =
    withReasonableAdjustments(dao.getByAlternativeEmailAddress(email)).map(Right.apply)

  override def findAtRisk(includeMentalHealth: Boolean)(implicit t: TimingContext): Future[ServiceResult[Set[AtRiskClient]]] = {
    withReasonableAdjustmentsSeq(
      dao.findAtRiskQuery(
        if (includeMentalHealth) Some(true) else None,
        Set(ClientRiskStatus.Medium, ClientRiskStatus.High)
      ).withClient.result
    ).flatMap(summaries =>
      ServiceResults.futureSequence(
        summaries.map { summary =>
          val casesAndEnquiries = ServiceResults.zip(
            caseService.findForClient(summary.client.universityID),
            enquiryService.findEnquiriesForClient(summary.client.universityID)
          )
          casesAndEnquiries.map(_.map {
            case (cases, enquiries) =>
              AtRiskClient(
                summary,
                cases.map(CaseService.lastModified).sorted(JavaTime.dateTimeOrdering).lastOption,
                enquiries.map(EnquiryService.lastModified).sorted(JavaTime.dateTimeOrdering).lastOption
              )
            case _ =>
              // Never actually used but good for typing
              AtRiskClient(summary, None, None)
          })
        }
      ).map(_.map(_.toSet))
    )
  }

  private def withReasonableAdjustments(result: DBIO[Option[(StoredClientSummary, StoredClient)]])(implicit t: TimingContext): Future[Option[ClientSummary]] = {
    daoRunner.run(for {
      summaryOption <- result
      adjustments <- summaryOption match {
        case Some((summary, _)) => dao.getReasonableAdjustmentsQuery(summary.universityID).result
        case None => DBIO.successful(Nil)
      }
    } yield {
      summaryOption.map { case (summary, client) => summary.asClientSummary(client.asClient, adjustments.map(_.reasonableAdjustment).toSet) }
    })
  }

  private def withReasonableAdjustmentsSeq(result: DBIO[Seq[(StoredClientSummary, StoredClient)]])(implicit t: TimingContext): Future[Seq[ClientSummary]] = {
    daoRunner.run(result).flatMap(summaries => Future.sequence(summaries.map { case (summary, client) =>
      daoRunner.run(dao.getReasonableAdjustmentsQuery(summary.universityID).result).map(adjustments =>
        summary.asClientSummary(client.asClient, adjustments.map(_.reasonableAdjustment).toSet)
      )
    }))
  }
}
