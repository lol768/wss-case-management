package services

import com.google.inject.ImplementedBy
import domain.Team
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import warwick.sso.{GroupName, GroupService, Usercode}

@ImplementedBy(classOf[PermissionServiceImpl])
trait PermissionService {
  def inTeam(user: Usercode, team: Team): ServiceResult[Boolean]
}

@Singleton
class PermissionServiceImpl @Inject() (
  groupService: GroupService,
  config: Configuration
) extends PermissionService {

  private val webgroupPrefix = config.get[String]("app.webgroup.team.prefix")

  private def webgroupFor(team: Team): GroupName =
    GroupName(s"$webgroupPrefix${team.id}")

  override def inTeam(user: Usercode, team: Team): ServiceResult[Boolean] =
    groupService.isUserInGroup(user, webgroupFor(team)).fold(
      e => Left(List(new ServiceError {
        override def message: String = e.getMessage
        override def cause = Some(e)
      })),
      r => Right(r)
    )

}


