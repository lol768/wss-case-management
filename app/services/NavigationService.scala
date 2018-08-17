package services

import com.google.inject.ImplementedBy
import domain.Teams
import javax.inject.{Inject, Singleton}
import play.api.mvc.Call
import system.Roles._
import warwick.sso.{LoginContext, Usercode}

sealed trait Navigation {
  def label: String

  def route: Call

  def children: Seq[Navigation]

  def dropdown: Boolean

  /**
    * Either this is the current page, or the current page is a child of this page
    */
  def isActive(path: String): Boolean = path.startsWith(route.url) || children.exists(_.isActive(path))

  def deepestActive(path: String): Option[Navigation] =
    if (path.startsWith(route.url) && !children.exists(_.isActive(path))) Some(this)
    else children.flatMap(_.deepestActive(path)).headOption
}

case class NavigationPage(
  label: String,
  route: Call,
  children: Seq[Navigation] = Nil
) extends Navigation {
  val dropdown = false
}

case class NavigationDropdown(
  label: String,
  route: Call,
  children: Seq[Navigation]
) extends Navigation {
  val dropdown = true
}

@ImplementedBy(classOf[NavigationServiceImpl])
trait NavigationService {
  def getNavigation(loginContext: LoginContext): Seq[Navigation]
}

@Singleton
class NavigationServiceImpl @Inject() (
  permission: PermissionService
) extends NavigationService {

  private lazy val masquerade = Option(NavigationPage("Masquerade", controllers.admin.routes.MasqueradeController.masquerade()))

  private lazy val teams = Teams.all.map { team =>
    team -> NavigationPage(s"${team.name} Team", controllers.admin.routes.AdminController.teamHome(team.id))
  }

  private def adminLinks(loginContext: LoginContext): Seq[Navigation] = {
    loginContext.user.map { user =>

      val links: Seq[NavigationPage] = Seq(
        masquerade.filter(_ => loginContext.actualUserHasRole(Sysadmin) || loginContext.actualUserHasRole(Masquerader)).toSeq

      ).flatten

      if (links.nonEmpty) {
        Seq(
          NavigationDropdown("Admin", controllers.admin.routes.MasqueradeController.masquerade(), links)
        )
      } else {
        Nil
      }

    }.getOrElse {
      Nil
    }
  }

  def teamLinks(login: LoginContext): Seq[NavigationPage] =
    login.user.map(_.usercode).map(teamLinksForUser).getOrElse(Nil)

  def teamLinksForUser(usercode: Usercode): Seq[NavigationPage] =
    teams.filter { case (team, page) =>
      permission.inTeam(usercode, team).getOrElse(false)
    }.map {
      case (team, page) => page
    }

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    adminLinks(loginContext) ++ teamLinks(loginContext)
}