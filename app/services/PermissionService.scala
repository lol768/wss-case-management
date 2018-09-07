package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{Team, Teams}
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Provider, Singleton}
import play.api.Configuration
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso.{GroupName, GroupService, RoleName, RoleService, User, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PermissionServiceImpl])
trait PermissionService {
  def inAnyTeam(user: Usercode): ServiceResult[Boolean]
  def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean]
  def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canAddMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canViewCase(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]]
  def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def webgroupFor(team: Team): GroupName
}

@Singleton
class PermissionServiceImpl @Inject() (
  groupService: GroupService,
  roleService: RoleService,
  enquiryServiceProvider: Provider[EnquiryService],
  caseServiceProvider: Provider[CaseService],
  config: Configuration,
  timing: TimingService
)(implicit ec: ExecutionContext) extends PermissionService {

  private lazy val enquiryService = enquiryServiceProvider.get()
  private lazy val caseService = caseServiceProvider.get()

  private val webgroupPrefix = config.get[String]("app.webgroup.team.prefix")

  private val adminRole = RoleName("admin")

  override def inAnyTeam(user: Usercode): ServiceResult[Boolean] =
    ServiceResults.sequence(Seq(isAdmin(user)) ++ Teams.all.map(inTeam(user, _)))
      .right.map(_.contains(true))

  override def canViewTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    ServiceResults.sequence(Seq(isAdmin(user), inTeam(user, team)))
      .right.map(_.contains(true))

  override def canViewEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      Future.successful(isAdmin(user.usercode)),
      Future.successful(inAnyTeam(user.usercode)),
      isEnquiryClient(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canAddMessageToEnquiry(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      Future.successful(isAdmin(user.usercode)),
      isEnquiryTeam(user.usercode, id),
      isEnquiryOwner(user.usercode, id),
      isEnquiryClient(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  override def canEditEnquiry(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      Future.successful(isAdmin(user)),
      isEnquiryTeam(user, id),
      isEnquiryOwner(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  private def isEnquiryTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.flatMap { case (enquiry, _) => inTeam(user, enquiry.team) } )

  private def isEnquiryOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).contains(user)))

  private def isEnquiryClient(user: User, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    enquiryService.get(id).map(_.map { case (enquiry, _) => enquiry.universityID == user.universityId.get } )

  override def canViewCase(user: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.successful(ServiceResults.sequence(Seq(
      isAdmin(user),
      inAnyTeam(user)
    )).right.map(_.contains(true)))

  override def canEditCase(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    Future.sequence(Seq(
      Future.successful(isAdmin(user)),
      isCaseTeam(user, id),
      isCaseOwner(user, id)
    )).map(results => ServiceResults.sequence(results).map(_.contains(true)))

  private def isCaseTeam(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    caseService.find(id).map(_.flatMap(c => inTeam(user, c.team)))

  private def isCaseOwner(user: Usercode, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Boolean]] =
    caseService.getOwners(Set(id)).map(_.map(_.getOrElse(id, Set()).contains(user)))

  override def webgroupFor(team: Team): GroupName =
    GroupName(s"$webgroupPrefix${team.id}")

  private def inTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, webgroupFor(team)).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      r => Right(r)
    )

  private def isAdmin(user: Usercode): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, roleService.getRole(adminRole).groupName).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      r => Right(r)
    )

}


