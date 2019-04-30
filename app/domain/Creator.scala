package domain

import warwick.core.helpers.ServiceResults.ServiceResult
import services.PermissionService

case class Creator(
  member: Member,
  teams: Seq[Team]
)

trait HasCreator {
  def teamMember: Member
}

case class EntityAndCreator[A <: HasCreator] (
  entity: A,
  creator: Creator
)

object EntityAndCreator {
  def apply[A <: HasCreator](entity: A, permissionsService: PermissionService): ServiceResult[EntityAndCreator[A]] = {
    permissionsService.teams(entity.teamMember.usercode).map(teams => {
      EntityAndCreator[A](entity, Creator(entity.teamMember, teams))
    })
  }
}