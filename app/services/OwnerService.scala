package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.{DaoRunner, OwnerDao}
import domain.{CaseOwner, EnquiryOwner, Owner}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsString, Json}
import warwick.core.timing.TimingContext
import warwick.sso.Usercode
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OwnerServiceImpl])
trait OwnerService {
  def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Usercode]]]]
  def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]]
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
    auditService.audit('CaseSetOwners, caseId.toString, 'Case, Json.arr(owners.map(o => JsString(o.string)))) {
      setOwners(
        owners,
        ownerDao.findCaseOwnersQuery(Set(caseId)),
        caseId,
        u => CaseOwner(
          caseId = caseId,
          userId = u
        )
      )
    }
  }

  override def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Usercode]]] = {
    auditService.audit('EnquirySetOwners, enquiryId.toString, 'Enquiry, Json.arr(owners.map(o => JsString(o.string)))) {
      setOwners(
        owners,
        ownerDao.findEnquiryOwnersQuery(Set(enquiryId)),
        enquiryId,
        u => EnquiryOwner(
          enquiryId = enquiryId,
          userId = u
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

}
