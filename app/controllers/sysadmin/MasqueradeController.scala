package controllers.sysadmin

import controllers.BaseController
import domain.{Teams, UserType}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.tabula.ProfileService
import services.{PhotoService, SecurityService}
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

@Singleton
class MasqueradeController @Inject()(
  securityService: SecurityService,
  profiles: ProfileService,
  photos: PhotoService,
  userLookupService: UserLookupService,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private[this] val testTabulaUsers = configuration.get[Seq[String]]("wellbeing.tabula.testUsers")
  private[this] val testTeamMemberUsers = configuration.get[Map[String, Seq[String]]]("wellbeing.testTeamMembers")
  private[this] val testAdminUsers = configuration.get[Seq[String]]("wellbeing.testAdmins")
  
  def masquerade: Action[AnyContent] = RequireMasquerader.async { implicit request =>
    profiles.getProfiles(testTabulaUsers.map(UniversityID.apply).toSet).successMap { profiles =>
      val testUsers =
        profiles.values
          .groupBy(_.department).toSeq
          .map { case (department, deptProfiles) =>
            department -> deptProfiles.groupBy(_.userType).toSeq
              .map { case (userType, userTypeProfiles) =>
                userType -> userTypeProfiles.groupBy(_.course).toSeq
                  .map { case (route, routeProfiles) =>
                    route -> routeProfiles.toSeq.sortBy(_.universityID.string)
                  }
                  .sortBy { case (course, _) => course.map(_.code) }
              }
              .sortBy { case (userType, _) => userType == UserType.Student }
          }
          .sortBy { case (dept, _) => dept.code }

      val testTeamMembers =
        testTeamMemberUsers.flatMap { case (teamId, usercodes) =>
          userLookupService.getUsers(usercodes.map(Usercode.apply)).toOption.map(_.values)
            .map { users =>
              Teams.fromId(teamId) ->
                users.toSeq.sortBy { u => (u.name.last, u.name.first, u.usercode.string) }
                  .map { user => user -> user.universityId.map(photos.photoUrl) }
            }
        }
        .toSeq
        .sortBy { case (team, _) => team.name }

      val testAdmins =
        userLookupService.getUsers(testAdminUsers.map(Usercode.apply))
          .toOption
          .map(_.values.toSeq.map { user => user -> user.universityId.map(photos.photoUrl) })
          .getOrElse(Seq())

      Ok(views.html.sysadmin.masquerade(testUsers, testTeamMembers, testAdmins))
    }
  }
}
