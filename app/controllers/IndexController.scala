package controllers

import javax.inject.Singleton

@Singleton
class IndexController extends BaseController {
  def home = Action { implicit request =>
    Ok(views.html.home())
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
