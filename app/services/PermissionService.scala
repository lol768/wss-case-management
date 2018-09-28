package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{Team, Teams}
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Provider, Singleton}
import play.api.Configuration
import system.Roles
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso.{GroupName, GroupService, RoleService, User, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PermissionServiceImpl])
trait PermissionService {
  def inAnyTeam(user: Usercode): Future[ServiceResult[Boolean]]
  def inAnyTeam(users: Set[Usercode]): ServiceResult[Map[Usercode, Boolean]]
  def teams(user: Usercode): ServiceResult[Seq[Team]]
  def isAdmin(user: Usercode): Future[ServiceResult[Boolean]]
  def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean]
  def canViewTeamFuture(user: Usercode, team: Team): Future[ServiceResult[Boolean]]

  def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddTeamMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canClientViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddClientMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def canViewCase(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddTeamMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddClientMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def canViewAppointment(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
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
)(implicit ec: ExecutionContext) extends PermissionService {

  private lazy val enquiryService = enquiryServiceProvider.get()
  private lazy val caseService = caseServiceProvider.get()
  private lazy val appointmentService = appointmentServiceProvider.get()

  private val webgroupPrefix = config.get[String]("app.webgroup.team.prefix")

  override def inAnyTeam(user: Usercode): Future[ServiceResult[Boolean]] =
    Future.successful(inAnyTeamImpl(user))

  private def inAnyTeamImpl(user: Usercode): ServiceResult[Boolean] =
      ServiceResults.sequence(Seq(isAdminImpl(user)) ++ Teams.all.map(inTeam(user, _)))
        .right.map(_.contains(true))

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

  override def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    ServiceResults.sequence(Seq(isAdminImpl(user), inTeam(user, team)))
      .right.map(_.contains(true))

  override def canViewTeamFuture(user: Usercode, team: Team): Future[ServiceResult[Boolean]] =
    Future.successful(canViewTeam(user, team))

  override def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user.usercode),
      inAnyTeam(user.usercode)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canAddTeamMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user.usercode),
      isEnquiryTeam(user.usercode, id),
      isEnquiryOwner(user.usercode, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canClientViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isEnquiryClient(user, id)

  override def canAddClientMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isEnquiryClient(user, id)

  override def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user),
      isEnquiryTeam(user, id),
      isEnquiryOwner(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  private def isEnquiryTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.flatMap { enquiry => inTeam(user, enquiry.team) } )

  private def isEnquiryOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    inAnyTeamImpl(user).fold(
      errors => Future.successful(Left(errors)),
      isInAnyTeam => if (!isInAnyTeam) {
        Future.successful(Right(false))
      } else {
        enquiryService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).contains(user)))
      }
    )

  private def isEnquiryClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.map { enquiry => enquiry.universityID == user.universityId.get } )

  override def canViewCase(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user),
      inAnyTeam(user)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user),
      isCaseTeam(user, id),
      isCaseOwner(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canAddTeamMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    canEditCase(user.usercode, id)

  override def canAddClientMessageToCase(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isCaseClient(user, id)

  private def isCaseTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    caseService.find(id).map(_.flatMap(c => inTeam(user, c.team)))

  private def isCaseOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    inAnyTeamImpl(user).fold(
      errors => Future.successful(Left(errors)),
      isInAnyTeam => if (!isInAnyTeam) {
        Future.successful(Right(false))
      } else {
        caseService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).contains(user)))
      }
    )

  def isCaseClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    user.universityId.map { uniId =>
      caseService.getClients(id).map {
        _.map(_.contains(uniId))
      }
    }.getOrElse {
      // No Uni ID; client of nothing
      Future.successful(Right(false))
    }

  override def canViewAppointment(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      isAdmin(user),
      inAnyTeam(user)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  private def isAppointmentClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    user.universityId.map { uniId =>
      appointmentService.getClients(id).map {
        _.map(_.exists(_.universityID == uniId))
      }
    }.getOrElse {
      // No Uni ID; client of nothing
      Future.successful(Right(false))
    }

  override def canClientManageAppointment(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    isAppointmentClient(user, id)

  override def webgroupFor(team: Team): GroupName =
    GroupName(s"$webgroupPrefix${team.id}")

  private def inTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, webgroupFor(team)).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      r => Right(r)
    )

  private def isAdminImpl(user: Usercode): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, roleService.getRole(Roles.Admin).groupName).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      r => Right(r)
    )

}


