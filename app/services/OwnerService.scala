package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.{DaoRunner, OwnerDao}
import domain.{CaseOwner, EnquiryOwner, Owner, OwnerVersion}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsString, Json}
import warwick.core.timing.TimingContext
import warwick.sso.Usercode
import domain.ExtendedPostgresProfile.api._
import helpers.JavaTime

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OwnerServiceImpl])
trait OwnerService {
  def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]
  def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]
  def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]]
  def getEnquiryOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]
  def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]
}

@Singleton
class OwnerServiceImpl @Inject()(
  auditService: AuditService,
  ownerDao: OwnerDao,
  daoRunner: DaoRunner,
)(implicit ec: ExecutionContext) extends OwnerService {

  override def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]] = {
    getOwners(ownerDao.findCaseOwnersQuery(ids))
  }

  override def getEnquiryOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]] = {
    getOwners(ownerDao.findEnquiryOwnersQuery(ids))
  }

  private def getOwners(daoQuery: Query[Owner.Owners, Owner, Seq])(implicit t: TimingContext) = {
    val query = daoQuery

    daoRunner.run(query.result)
      .map(_.groupBy(_.entityId).mapValues(_.map(_.userId).toSet))
      .map(Right.apply)
  }

  override def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] = {
    val now = JavaTime.offsetDateTime
    auditService.audit('CaseSetOwners, caseId.toString, 'Case, Json.arr(owners.map(o => JsString(o.string)))) {
      setOwners(
        owners,
        ownerDao.findCaseOwnersQuery(Set(caseId)),
        caseId,
        u => CaseOwner(
          caseId = caseId,
          userId = u,
          version = now,
        )
      )
    }
  }

  override def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] = {
    auditService.audit('EnquirySetOwners, enquiryId.toString, 'Enquiry, Json.arr(owners.map(o => JsString(o.string)))) {
      val now = JavaTime.offsetDateTime
      setOwners(
        owners,
        ownerDao.findEnquiryOwnersQuery(Set(enquiryId)),
        enquiryId,
        u => EnquiryOwner(
          enquiryId = enquiryId,
          userId = u,
          version = now,
        )
      )
    }
  }

  private def setOwners(owners: Set[Usercode], existingQuery: Query[Owner.Owners, Owner, Seq], entityId: UUID, builder: Usercode => Owner)(implicit ac: AuditLogContext) = {
    val existing = existingQuery.result

    val needsRemoving = existing.map(_.filterNot(e => owners.contains(e.userId)))
    val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(ownerDao.delete)))

    val needsAdding = existing.map(e => owners.toSeq.filterNot(e.map(_.userId).contains))
    val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(o =>
      ownerDao.insert(builder(o))
    )))

    daoRunner.run(DBIO.seq(removals, additions)).flatMap(_ =>
      daoRunner.run(existingQuery.result).map(_.map(_.userId).toSet)
    ).map(Right.apply)
  }

  override def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]] = {
    daoRunner.run(ownerDao.getCaseOwnerHistory(caseId)).map(Right.apply)
  }

}
