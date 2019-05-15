package services

import com.google.inject.ImplementedBy
import domain.Team
import javax.inject.{Inject, Singleton}
import play.api.Configuration
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
  permission: PermissionService,
  configuration: Configuration,
) extends NavigationService {

  private[this] val dataGenerationEnabled = configuration.get[Boolean]("wellbeing.dummyDataGeneration")

  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())

  private lazy val reportingAdmin =
    NavigationPage("Reports", controllers.reports.routes.ReportsController.home())

  private lazy val admin =
    NavigationDropdown("Admin", Call("GET", "/admin"), Seq(
      reportingAdmin,
      NavigationPage("Locations", controllers.locations.routes.LocationsController.list())
    ))

  private lazy val sysadmin =
    NavigationDropdown("Sysadmin", Call("GET", "/sysadmin"), Seq(
      Some(masquerade),
      Some(NavigationPage("Email queue", controllers.sysadmin.routes.EmailQueueController.queued())),
      Some(NavigationPage("Import data", controllers.sysadmin.routes.DataImportController.importForm())),
      Some(NavigationPage("Dummy data generation", controllers.sysadmin.routes.DataGenerationController.generateForm()))
        .filter(_ => dataGenerationEnabled),
    ).flatten)

  private def teamHome(team: Team) = NavigationPage(team.name, controllers.admin.routes.AdminController.teamHome(team.id))

  private def adminOrReportingMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.userHasRole(Admin))
      Seq(admin)
    else if (loginContext.userHasRole(ReportingAdmin))
      Seq(reportingAdmin)
    else
      Nil

  private def sysadminMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.actualUserHasRole(Sysadmin))
      Seq(sysadmin)
    else
      Nil

  private def masqueraderLinks(loginContext: LoginContext): Seq[Navigation] =
    Seq(masquerade).filter(_ => !loginContext.actualUserHasRole(Sysadmin) && loginContext.actualUserHasRole(Masquerader))

  def teamLinks(login: LoginContext): Seq[NavigationPage] =
    login.user.map(_.usercode).map(teamLinksForUser).getOrElse(Nil)

  def teamLinksForUser(usercode: Usercode): Seq[NavigationPage] = {
    val teamLinks = permission.teams(usercode).right.map(_.map(teamHome)).getOrElse(Nil)

    if(teamLinks.nonEmpty) {
      new NavigationPage("Home", controllers.routes.IndexController.home()) {
        override def isActive(path: String): Boolean = path.equals(route.url)
        override val children: Seq[Navigation] = Seq(NavigationPage("Preferences", controllers.routes.UserPreferencesController.form()))
      } +: teamLinks
    } else {
      teamLinks
    }
  }

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    teamLinks(loginContext) ++
    masqueraderLinks(loginContext) ++
    adminOrReportingMenu(loginContext) ++
    sysadminMenu(loginContext)
}
