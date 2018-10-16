package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.MemberDao.StoredMember
import domain.dao.MemberDao.StoredMember.Members
import domain.dao.{DaoRunner, OwnerDao}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsString, Json}
import warwick.core.timing.TimingContext
import warwick.sso.Usercode
import helpers.ServiceResults.Implicits._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OwnerServiceImpl])
trait OwnerService {
  def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]
  def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Member]]]
  def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]]
  def getEnquiryOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]
  def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Member]]]
}

@Singleton
class OwnerServiceImpl @Inject()(
  auditService: AuditService,
  memberService: MemberService,
  ownerDao: OwnerDao,
  daoRunner: DaoRunner,
)(implicit ec: ExecutionContext) extends OwnerService {

  override def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]] = {
    getOwners(ownerDao.findCaseOwnersQuery(ids).withMember)
  }

  override def getEnquiryOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]] = {
    getOwners(ownerDao.findEnquiryOwnersQuery(ids).withMember)
  }

  private def getOwners(daoQuery: Query[(Owner.Owners, Members), (Owner, StoredMember), Seq])(implicit t: TimingContext) = {
    val query = daoQuery

    daoRunner.run(query.result)
      .map(_.groupBy { case (o, _) => o.entityId }.mapValues(_.map { case (_, m) => m.asMember }.toSet))
      .map(Right.apply)
  }

  override def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Member]]] = {
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

  override def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Set[Member]]] = {
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
    memberService.getOrAddMembers(owners).successFlatMapTo { members =>
      daoRunner.run(for {
        result <- updateDifferencesDBIO[Owner, Usercode](
          owners,
          existingQuery,
          _.userId,
          builder,
          ownerDao.insert,
          ownerDao.delete
        )
      } yield {
        Right(result.all.map(o => members.find(_.usercode == o.userId).get).toSet)
      })
    }
  }

  override def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]] = {
    daoRunner.run(ownerDao.getCaseOwnerHistory(caseId)).map(Right.apply)
  }

}
