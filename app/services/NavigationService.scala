package services

import com.google.inject.ImplementedBy
import javax.inject.Singleton
import play.api.mvc.Call
import system.Roles._
import warwick.sso.LoginContext

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
class NavigationServiceImpl extends NavigationService {
  private def adminLinks(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.actualUserHasRole(Sysadmin) || loginContext.actualUserHasRole(Masquerader)) {
      Seq(
        NavigationDropdown("Admin", controllers.routes.MasqueradeController.masquerade(), Seq(
          NavigationPage("Masquerade", controllers.routes.MasqueradeController.masquerade())
        ))
      )
    } else {
      Nil
    }

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    adminLinks(loginContext)
}