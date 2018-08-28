package services

import com.google.inject.ImplementedBy
import domain.{Team, Teams}
import helpers.ServiceResults
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import warwick.core.timing.TimingService
import warwick.sso.{GroupName, GroupService, Usercode}

@ImplementedBy(classOf[PermissionServiceImpl])
trait PermissionService {
  def webgroupFor(team: Team): GroupName
  def inTeam(user: Usercode, team: Team): ServiceResult[Boolean]
  def inAnyTeam(user: Usercode): ServiceResult[Boolean]
}

@Singleton
class PermissionServiceImpl @Inject() (
  groupService: GroupService,
  config: Configuration,
  timing: TimingService
) extends PermissionService {
  import timing._

  private val webgroupPrefix = config.get[String]("app.webgroup.team.prefix")

  override def webgroupFor(team: Team): GroupName =
    GroupName(s"$webgroupPrefix${team.id}")

  override def inTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, webgroupFor(team)).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      r => Right(r)
    )

  override def inAnyTeam(user: Usercode): ServiceResult[Boolean] =
    ServiceResults.sequence(Teams.all.map(inTeam(user, _)))
      .right.map(_.contains(true))

}


