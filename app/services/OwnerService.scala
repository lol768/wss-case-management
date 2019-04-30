package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.MemberDao.StoredMember
import domain.dao.MemberDao.StoredMember.Members
import domain.dao.{DaoRunner, OwnerDao}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsString, Json}
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OwnerServiceImpl])
trait OwnerService {
  def getCaseOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]
  def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]]
  def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]]
  def getEnquiryOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[Member]]]]
  def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]]
  def getAppointmentOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[AppointmentTeamMember]]]]
  def setAppointmentOwners(appointmentId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]]
  def setAppointmentOutlookId(appointmentId: UUID, owner: Usercode, outlookId: String)(implicit ac: AuditLogContext): Future[ServiceResult[Owner]]
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

  override def getAppointmentOwners(ids: Set[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, Set[AppointmentTeamMember]]]] = {
    daoRunner.run(ownerDao.findAppointmentOwnersQuery(ids).withMember.result)
      .map(_.groupBy { case (o, _) => o.entityId }.mapValues(_.map { case (o, m) => AppointmentTeamMember(m.asMember, o.outlookId) }.toSet))
      .map(Right.apply)
  }

  private def getOwners(daoQuery: Query[(Owner.Owners, Members), (Owner, StoredMember), Seq])(implicit t: TimingContext) = {
    val query = daoQuery

    daoRunner.run(query.result)
      .map(_.groupBy { case (o, _) => o.entityId }.mapValues(_.map { case (_, m) => m.asMember }.toSet))
      .map(Right.apply)
  }

  override def setCaseOwners(caseId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]] = {
    val now = JavaTime.offsetDateTime
    auditService.audit(Operation.Case.SetOwners, caseId.toString, Target.Case, Json.arr(owners.map(o => JsString(o.string)))) {
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

  override def setEnquiryOwners(enquiryId: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]] = {
    auditService.audit(Operation.Enquiry.SetOwners, enquiryId.toString, Target.Enquiry, Json.arr(owners.map(o => JsString(o.string)))) {
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

  override def setAppointmentOwners(appointmentID: UUID, owners: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[UpdateDifferencesResult[Owner]]] = {
    auditService.audit(Operation.Appointment.SetOwners, appointmentID.toString, Target.Appointment, Json.arr(owners.map(o => JsString(o.string)))) {
      val now = JavaTime.offsetDateTime
      setOwners(
        owners,
        ownerDao.findAppointmentOwnersQuery(Set(appointmentID)),
        appointmentID,
        u => AppointmentOwner(
          appointmentID = appointmentID,
          userId = u,
          version = now,
        )
      )
    }
  }

  override def setAppointmentOutlookId(appointmentId: UUID, owner: Usercode, outlookId: String)(implicit ac: AuditLogContext): Future[ServiceResult[Owner]] = {
    daoRunner.run(for {
      owners <- ownerDao.findAppointmentOwnersQuery(Set(appointmentId)).filter(o => o.userId === owner).result
      updated <- ownerDao.update(owners.head.copy(outlookId = Some(outlookId)), owners.head.version)
    } yield Right(updated))
  }

  private def setOwners(owners: Set[Usercode], existingQuery: Query[Owner.Owners, Owner, Seq], entityId: UUID, builder: Usercode => Owner)(implicit ac: AuditLogContext) = {
    memberService.getOrAddMembers(owners).successFlatMapTo { _ =>
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
        Right(result)
      })
    }
  }

  override def getCaseOwnerHistory(caseId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[OwnerVersion]]] = {
    daoRunner.run(ownerDao.getCaseOwnerHistory(caseId)).map(Right.apply)
  }

}
