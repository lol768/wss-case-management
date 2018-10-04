package domain

import helpers.ServiceResults.ServiceResult
import services.PermissionService
import warwick.sso.{User, UserLookupService, Usercode}

case class Creator(
  user: Either[Usercode, User],
  teams: Seq[Team]
)

trait HasCreator {
  def teamMember: Usercode
}

case class EntityAndCreator[A <: HasCreator] (
  entity: A,
  creator: Creator
)

object EntityAndCreator {
  def apply[A <: HasCreator](entity: A, permissionsService: PermissionService, userLookupService: UserLookupService): ServiceResult[EntityAndCreator[A]] = {
    permissionsService.teams(entity.teamMember).map(teams => {
      val user = userLookupService.getUser(entity.teamMember).toEither.left.map(_ => entity.teamMember)
      EntityAndCreator[A](entity, Creator(user, teams))
    })
  }
}