package controllers

import javax.inject.{Inject, Singleton}
import services.SecurityService

@Singleton
class IndexController @Inject()(
  securityService: SecurityService
) extends BaseController {
  import securityService._

  def home = SigninRequiredAction { implicit request =>
    Ok(views.html.home())
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
