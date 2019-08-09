package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{IssueState, Team, Teams}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Provider, Singleton}
import play.api.Configuration
import system.Roles
import warwick.core.Logging
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PermissionServiceImpl])
trait PermissionService {
  def inAnyTeam(user: Usercode): Future[ServiceResult[Boolean]]
  def inAnyTeam(users: Set[Usercode]): ServiceResult[Map[Usercode, Boolean]]
  def teams(user: Usercode): ServiceResult[Seq[Team]]
  def isAdmin(user: Usercode): Future[ServiceResult[Boolean]]
  def isReportingAdmin(user: Usercode): Future[ServiceResult[Boolean]]
  def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean]
  def canViewTeamFuture(user: Usercode, team: Team): Future[ServiceResult[Boolean]]

  def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddTeamMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canClientViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddClientMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def canViewCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditCaseNote(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddTeamMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canClientViewCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddClientMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def canViewAppointment(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditAppointment(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canClientManageAppointment(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def webgroupFor(team: Team): GroupName
}

@Singleton
class PermissionServiceImpl @Inject() (
  groupService: GroupService,
  roleService: RoleService,
  enquiryServiceProvider: Provider[EnquiryService],
  caseServiceProvider: Provider[CaseService],
  appointmentServiceProvider: Provider[AppointmentService],
  config: Configuration,
  timing: TimingService
)(implicit ec: ExecutionContext) extends PermissionService with Logging {

  private lazy val enquiryService = enquiryServiceProvider.get()
  private lazy val caseService = caseServiceProvider.get()
  private lazy val appointmentService = appointmentServiceProvider.get()

  private lazy val webgroupPrefix = config.get[String]("app.webgroup.team.prefix")
  private lazy val teamRestrictedPermissions = config.get[Boolean]("wellbeing.features.teamRestrictedPermissions")

  override def inAnyTeam(user: Usercode): Future[ServiceResult[Boolean]] =
    Future.successful(inAnyTeamImpl(user))

  private def inAnyTeamImpl(user: Usercode): ServiceResult[Boolean] =
      ServiceResults.sequence(Seq(isAdminImpl(user)) ++ Teams.all.map(inTeam(user, _)))
        .right.map(_.contains(true))

  private def oneOf(checks: Future[ServiceResult[Boolean]] *): Future[ServiceResult[Boolean]] =
    Future.sequence(checks).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  private def forAll(checks: Future[ServiceResult[Boolean]] *): Future[ServiceResult[Boolean]] =
    Future.sequence(checks).map(results => ServiceResults.sequence(results).map(_.forall(is => is)))


  override def inAnyTeam(users: Set[Usercode]): ServiceResult[Map[Usercode, Boolean]] = {
    users.toSeq.map(user => user -> inAnyTeamImpl(user)).partition { case (_, result) => result.isLeft } match {
      case (Nil, results) => Right(results.collect { case (user, Right(x)) => user -> x }.toMap)
      case (errors, _) => Left(errors.toList.collect { case (_, Left(x)) => x }.flatten)
    }
  }

  override def teams(user: Usercode): ServiceResult[Seq[Team]] =
    Right(Teams.all.filter(canViewTeam(user, _).getOrElse(false)))

  override def isAdmin(user: Usercode): Future[ServiceResult[Boolean]] =
    Future.successful(isAdminImpl(user))

  override def isReportingAdmin(user: Usercode): Future[ServiceResult[Boolean]] =
    Future.successful(isReportingAdminImpl(user))

  override def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    ServiceResults.sequence(Seq(isAdminImpl(user), if (teamRestrictedPermissions) inTeam(user, team) else inAnyTeamImpl(user)))
      .right.map(_.contains(true))

  override def canViewTeamFuture(user: Usercode, team: Team): Future[ServiceResult[Boolean]] =
    Future.successful(canViewTeam(user, team))

  override def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user.usercode),
      inAnyTeam(user.usercode)
    )

  override def canAddTeamMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user.usercode),
      if (teamRestrictedPermissions) isEnquiryTeam(user.usercode, id) else inAnyTeam(user.usercode),
      isEnquiryOwner(user.usercode, id)
    )

  override def canClientViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isEnquiryClient(user, id)

  override def canAddClientMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    forAll(
      isEnquiryClient(user, id),
      isEnquiryOpen(id)
    )

  override def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user),
      if (teamRestrictedPermissions) isEnquiryTeam(user, id) else inAnyTeam(user),
      isEnquiryOwner(user, id)
    )

  private def isEnquiryTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.flatMap { enquiry => inTeam(user, enquiry.team) } )

  private def isEnquiryOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.successful(inAnyTeamImpl(user)).successFlatMapTo { isInAnyTeam =>
      if (!isInAnyTeam) {
        Future.successful(Right(false))
      } else {
        enquiryService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).map(_.usercode).contains(user)))
      }
    }

  private def isEnquiryClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.map { enquiry => enquiry.client.universityID == user.universityId.get } )

  private def isEnquiryOpen(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.map(_.state != IssueState.Closed))

  override def canViewCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    canEditCase(user, id) // view/edit permissions are the same at the moment

  override def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user),
      if (teamRestrictedPermissions) isCaseTeam(user, id) else inAnyTeam(user),
      isCaseOwner(user, id)
    )

  override def canEditCaseNote(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] = {
    caseService.getNote(id).successFlatMapTo(noteAndCase => {
      canEditCase(user, noteAndCase.clientCase.id).successMapTo(canEditNote => {
        canEditNote && noteAndCase.note.teamMember.usercode == user
      })
    })
  }

  override def canAddTeamMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    canEditCase(user.usercode, id)

  override def canClientViewCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    forAll(
      isCaseClient(user, id),
      caseHasMessages(user, id)
    )


  override def canAddClientMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    forAll(
      isCaseClient(user, id),
      caseHasMessages(user, id)
    )

  private def isCaseTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    caseService.find(id).map(_.flatMap(c => inTeam(user, c.team)))

  private def isCaseOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.successful(inAnyTeamImpl(user)).successFlatMapTo { isInAnyTeam =>
      if (!isInAnyTeam) {
        Future.successful(Right(false))
      } else {
        caseService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).map(_.usercode).contains(user)))
      }
    }

  private def isCaseClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    user.universityId.map { uniId =>
      caseService.getClients(id).map {
        _.map(_.exists(_.universityID == uniId))
      }
    }.getOrElse {
      // No Uni ID; client of nothing
      Future.successful(Right(false))
    }

  private def caseHasMessages(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    user.universityId.map { uniId =>
      caseService.hasMessagesForClient(id, uniId)
    }.getOrElse {
      // No Uni ID; client of nothing
      Future.successful(Right(false))
    }

  override def canViewAppointment(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user),
      inAnyTeam(user)
    )

  override def canEditAppointment(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    oneOf(
      isAdmin(user),
      if (teamRestrictedPermissions) isAppointmentTeam(user, id) else inAnyTeam(user),
      isAppointmentTeamMember(user, id)
    )

  private def isAppointmentClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    user.universityId.map { uniId =>
      appointmentService.getClients(id).map {
        _.map(_.exists(_.client.universityID == uniId))
      }
    }.getOrElse {
      // No Uni ID; client of nothing
      Future.successful(Right(false))
    }

  override def canClientManageAppointment(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isAppointmentClient(user, id)

  private def isAppointmentTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    appointmentService.find(id).map(_.flatMap(a => inTeam(user, a.team)))

  private def isAppointmentTeamMember(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    inAnyTeamImpl(user).fold(
      errors => Future.successful(Left(errors)),
      isInAnyTeam =>
        if (!isInAnyTeam) {
          Future.successful(Right(false))
        } else {
          appointmentService.getTeamMembers(id).map(_.map(_.map(_.member.usercode).contains(user)))
        }
    )

  override def webgroupFor(team: Team): GroupName =
    GroupName(s"$webgroupPrefix${team.id}")

  private def inTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    ServiceResults.fromTry(groupService.isUserInGroup(user, webgroupFor(team)))

  private def isAdminImpl(user: Usercode): ServiceResult[Boolean] =
    ServiceResults.fromTry(groupService.isUserInGroup(user, roleService.getRole(Roles.Admin).groupName))

  private def isReportingAdminImpl(user: Usercode): ServiceResult[Boolean] =
    ServiceResults.fromTry(groupService.isUserInGroup(user, roleService.getRole(Roles.ReportingAdmin).groupName))

}


