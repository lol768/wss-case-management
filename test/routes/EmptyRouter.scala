package routes

import play.api.routing.Router
import play.api.routing.Router.Routes

/**
  * Blank router for use by minimal application.
  * Overriding Guice binding for Router doesn't seem to work as it should,
  * so instead we set the play.http.router property to a class.
  */
class EmptyRouter extends Router {
  import play.api.routing.Router.empty

  override def routes: Routes = empty.routes
  override def documentation: Seq[(String, String, String)] = empty.documentation
  override def withPrefix(prefix: String): Router = empty.withPrefix(prefix)
}
