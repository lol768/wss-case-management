package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.CaseTag
import domain.dao.CaseDao.{Case, StoredCaseTag}
import domain.dao.{CaseDao, DaoRunner}
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import play.api.libs.json.{JsString, Json}
import warwick.core.timing.TimingContext
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CaseServiceImpl])
trait CaseService {
  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]]
  def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]]
  def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]]
}

class CaseServiceImpl @Inject() (
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: CaseDao
)(implicit ec: ExecutionContext) extends CaseService {

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(id)).map(Right(_))

  override def getCaseTags(caseIds: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[CaseTag]]]] =
    daoRunner.run(dao.findTagsQuery(caseIds).result)
      .map(_.groupBy(_.caseId).mapValues(_.map(_.caseTag).toSet))
      .map(Right.apply)

  override def setCaseTags(caseId: UUID, tags: Set[CaseTag])(implicit ac: AuditLogContext): Future[ServiceResult[Set[CaseTag]]] =
    auditService.audit('CaseSetTags, caseId.toString, 'Case, Json.toJson(tags)) {
      val existing = dao.findTagsQuery(Set(caseId)).result

      val needsRemoving = existing.map(_.filterNot(e => tags.contains(e.caseTag)))
      val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(dao.deleteTag)))

      val needsAdding = existing.map(e => tags.toSeq.filterNot(e.map(_.caseTag).contains))
      val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(t =>
        dao.insertTag(StoredCaseTag(caseId, t))
      )))

      daoRunner.run(DBIO.seq(removals, additions))
        .map(_ => Right(tags))
    }
}
