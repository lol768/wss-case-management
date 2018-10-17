package services

import com.google.inject.ImplementedBy
import domain.Team
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
  route: Call
) extends Navigation {
  val dropdown = false
  val children: Seq[Navigation] = Nil

  def withChildren(children: Seq[Navigation]) = NavigationDropdown(label, route, children)
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

  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())

  private lazy val admin =
    NavigationDropdown("Admin", Call("GET", "/admin"), Seq(
      NavigationPage("Reports", controllers.reports.routes.ReportsController.home()),
      NavigationPage("Locations", controllers.locations.routes.LocationsController.list())
    ))

  private def teamHome(team: Team) = NavigationPage(team.name, controllers.admin.routes.AdminController.teamHome(team.id))

  private def adminMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.userHasRole(Admin))
      Seq(admin)
    else
      Nil

  private def sysadminMenu(loginContext: LoginContext): Seq[Navigation] =
    Nil

  private def masqueraderLinks(loginContext: LoginContext): Seq[Navigation] =
    Seq(masquerade).filter(_ => loginContext.actualUserHasRole(Masquerader))

  def teamLinks(login: LoginContext): Seq[NavigationPage] =
    login.user.map(_.usercode).map(teamLinksForUser).getOrElse(Nil)

  def teamLinksForUser(usercode: Usercode): Seq[NavigationPage] = {
    val teamLinks = permission.teams(usercode).right.map(_.map(teamHome)).getOrElse(Nil)

    if(teamLinks.nonEmpty) {
      new NavigationPage("Me", controllers.routes.IndexController.home()) {
        override def isActive(path: String): Boolean = path.equals(route.url)
      } +: teamLinks
    } else {
      teamLinks
    }
  }

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    teamLinks(loginContext) ++
    masqueraderLinks(loginContext) ++
    adminMenu(loginContext) ++
    sysadminMenu(loginContext)
}