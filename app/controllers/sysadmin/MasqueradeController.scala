package controllers.sysadmin

import controllers.BaseController
import domain.UserType
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import services.tabula.ProfileService
import system.Roles.Masquerader
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@Singleton
class MasqueradeController @Inject()(
  securityService: SecurityService,
  profiles: ProfileService,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private[this] val testUsers = configuration.get[Seq[String]]("wellbeing.tabula.testUsers")
  
  def masquerade: Action[AnyContent] = RequiredActualUserRoleAction(Masquerader).async { implicit request =>
    profiles.getProfiles(testUsers.map(UniversityID.apply).toSet).successMap { profiles =>
      val testProfiles =
        profiles.values
          .groupBy(_.department).toSeq
          .map { case (department, deptProfiles) =>
            department -> deptProfiles.groupBy(_.userType).toSeq
              .map { case (userType, userTypeProfiles) =>
                userType -> userTypeProfiles.groupBy(_.route).toSeq
                  .map { case (route, routeProfiles) =>
                    route -> routeProfiles.toSeq.sortBy(_.universityID.string)
                  }
                  .sortBy { case (route, _) => route.map(_.code) }
              }
              .sortBy { case (userType, _) => userType == UserType.Student }
          }
          .sortBy { case (dept, _) => dept.code }

      Ok(views.html.sysadmin.masquerade(testProfiles))
    }
  }
}
