package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.dao.ClientConsultationDao.StoredClientConsultation
import domain.dao.{ClientConsultationDao, DaoRunner}
import domain.{ClientConsultation, ClientConsultationSave}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientConsultationServiceImpl])
trait ClientConsultationService {
  def save(universityID: UniversityID, consultation: ClientConsultationSave, teamMember: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[ClientConsultation]]
  def update(universityID: UniversityID, id: UUID, consultation: ClientConsultationSave, teamMember: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientConsultation]]
  def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[ClientConsultation]]]
  def getAll(universityIDs: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Seq[ClientConsultation]]]]
}

@Singleton
class ClientConsultationServiceImpl @Inject()(
  auditService: AuditService,
  clientService: ClientService,
  dao: ClientConsultationDao,
  daoRunner: DaoRunner,
)(implicit executionContext: ExecutionContext) extends ClientConsultationService {

  override def save(universityID: UniversityID, consultation: ClientConsultationSave, teamMember: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[ClientConsultation]] = {
    val id = UUID.randomUUID()
    auditService.audit(
      Operation.ClientConsultation.Save,
      id.toString,
      Target.ClientConsultation,
      Json.toJson(consultation)(ClientConsultationSave.formatter)
    ) {
      clientService.getOrAddClients(Set(universityID)).successFlatMapTo(_ =>
        daoRunner.run(for {
          _ <- dao.insert(StoredClientConsultation(
            id = id,
            universityID = universityID,
            reason = consultation.reason,
            suggestedResolution = consultation.suggestedResolution,
            alreadyTried = consultation.alreadyTried,
            sessionFeedback = consultation.sessionFeedback,
            administratorOutcomes = consultation.administratorOutcomes,
            created = JavaTime.offsetDateTime,
            lastUpdatedBy = teamMember,
            version = JavaTime.offsetDateTime,
          ))
          inserted <-
            dao.findSnippetsQuery
              .filter(_.id === id)
              .map(_.clientConsultation)
              .result.head
        } yield inserted).map(ServiceResults.success)
      )
    }
  }

  override def update(universityID: UniversityID, id: UUID, consultation: ClientConsultationSave, teamMember: Usercode, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[ClientConsultation]] =
    auditService.audit(
      Operation.ClientConsultation.Update,
      id.toString,
      Target.ClientConsultation,
      Json.toJson(consultation)(ClientConsultationSave.formatter)
    ) {
      clientService.getOrAddClients(Set(universityID)).successFlatMapTo(_ =>
        daoRunner.run(for {
          existing <- dao.findSnippetsQuery.filter(_.id === id).result.head
          if existing.universityID == universityID

          _ <- dao.update(StoredClientConsultation(
            id = existing.id,
            universityID = existing.universityID,
            reason = consultation.reason,
            suggestedResolution = consultation.suggestedResolution,
            alreadyTried = consultation.alreadyTried,
            sessionFeedback = consultation.sessionFeedback,
            administratorOutcomes = consultation.administratorOutcomes,
            created = existing.created,
            lastUpdatedBy = teamMember,
            version = JavaTime.offsetDateTime,
          ), version)

          updated <-
            dao.findSnippetsQuery
              .filter(_.id === id)
              .map(_.clientConsultation)
              .result.head
        } yield updated).map(ServiceResults.success)
      )
    }

  override def get(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[ClientConsultation]]] =
    daoRunner.run(
      dao.findSnippetsQuery
        .filter(_.universityID === universityID)
        .sortBy(_.created)
        .map(_.clientConsultation)
        .result
    ).map(ServiceResults.success)

  override def getAll(universityIDs: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, Seq[ClientConsultation]]]] =
    daoRunner.run(
      dao.findSnippetsQuery
        .filter(_.universityID.inSet(universityIDs))
        .sortBy(_.created)
        .map(_.clientConsultation)
        .result
    ).map { consultations =>
      ServiceResults.success(universityIDs.map { universityID =>
        universityID -> consultations.filter(_.universityID == universityID)
      }.toMap)
    }

}
