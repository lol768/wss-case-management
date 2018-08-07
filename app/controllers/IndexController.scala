package controllers

import domain.Teams
import javax.inject.{Inject, Singleton}
import services.SecurityService

@Singleton
class IndexController @Inject()(
  securityService: SecurityService
) extends BaseController {
  import securityService._

  def home = SigninRequiredAction { implicit request =>
    Ok(views.html.home(Teams.all))
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
