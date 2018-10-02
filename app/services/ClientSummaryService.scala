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
import services.tabula.ProfileService

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
  profileService: ProfileService,
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
          dao.insertReasonableAdjustments,
          dao.deleteReasonableAdjustments
        )
        updatedAdjustments <- dao.getReasonableAdjustmentsQuery(universityID).result
      } yield {
        Right(updated.asClientSummary(updatedAdjustments.map(_.reasonableAdjustment).toSet))
      })
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
      ).result
    ).flatMap(summaries =>
      profileService.getProfiles(summaries.map(_.universityID).toSet).flatMap(_.fold(
        errors => Future.successful(Left(errors)),
        profileMap =>
          ServiceResults.futureSequence(
            summaries.map { summary =>
              val casesAndEnquiries = ServiceResults.zip(
                caseService.findForClient(summary.universityID),
                enquiryService.findEnquiriesForClient(summary.universityID)
              )
              casesAndEnquiries.map(_.map {
                case (cases, enquiries) =>
                  AtRiskClient(
                    summary,
                    profileMap.get(summary.universityID),
                    cases.map(CaseService.lastModified).sorted(JavaTime.dateTimeOrdering).lastOption,
                    enquiries.map(EnquiryService.lastModified).sorted(JavaTime.dateTimeOrdering).lastOption
                  )
                case _ =>
                  // Never actually used but good for typing
                  AtRiskClient(summary, None, None, None)
              })
            }
          ).map(_.map(_.toSet))
      ))
    )
  }

  private def withReasonableAdjustments(result: DBIO[Option[StoredClientSummary]])(implicit t: TimingContext): Future[Option[ClientSummary]] = {
    daoRunner.run(for {
      summaryOption <- result
      adjustments <- summaryOption match {
        case Some(summary) => dao.getReasonableAdjustmentsQuery(summary.universityID).result
        case None => DBIO.successful(Nil)
      }
    } yield {
      summaryOption.map(_.asClientSummary(adjustments.map(_.reasonableAdjustment).toSet))
    })
  }

  private def withReasonableAdjustmentsSeq(result: DBIO[Seq[StoredClientSummary]])(implicit t: TimingContext): Future[Seq[ClientSummary]] = {
    daoRunner.run(result).flatMap(summaries => Future.sequence(summaries.map(summary =>
      daoRunner.run(dao.getReasonableAdjustmentsQuery(summary.universityID).result).map(adjustments =>
        summary.asClientSummary(adjustments.map(_.reasonableAdjustment).toSet)
      )
    )))
  }
}
