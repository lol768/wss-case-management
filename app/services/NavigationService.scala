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

  private lazy val sysadmin = NavigationPage("Sysadmin", Call("GET", "/sysadmin"))
  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())

  private lazy val admin =
    NavigationDropdown("Admin", Call("GET", "/admin"), Seq(
      NavigationPage("Reports", controllers.reports.routes.ReportsController.home())
    ))

  private def teamHome(team: Team) = NavigationPage(s"${team.name} team", controllers.admin.routes.AdminController.teamHome(team.id))

  private def adminLinks(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.userHasRole(Admin))
      Seq(admin)
    else
      Nil

  private def sysadminLinks(loginContext: LoginContext): Seq[Navigation] =
    loginContext.user.map { _ =>
      val links: Seq[NavigationPage] = Seq(
        Some(masquerade).filter(_ => loginContext.actualUserHasRole(Masquerader)).toSeq
      ).flatten

      if (links.nonEmpty) {
        Seq(
          sysadmin.withChildren(links)
        )
      } else {
        Nil
      }
    }.getOrElse {
      Nil
    }

  def teamLinks(login: LoginContext): Seq[NavigationPage] =
    login.user.map(_.usercode).map(teamLinksForUser).getOrElse(Nil)

  def teamLinksForUser(usercode: Usercode): Seq[NavigationPage] =
    permission.teams(usercode).right.map(_.map(teamHome)).getOrElse(Nil)

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    teamLinks(loginContext) ++ adminLinks(loginContext) ++ sysadminLinks(loginContext)
}