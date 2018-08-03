package controllers

import javax.inject.{Inject, Singleton}

import play.api.inject.ApplicationLifecycle
import play.api.libs.json._
import play.api.mvc._
import services.healthcheck.HealthCheck

import scala.collection.JavaConverters._
import scala.concurrent.Future

@Singleton
class ServiceCheckController @Inject()(
  life: ApplicationLifecycle,
  healthChecks: java.util.Set[HealthCheck]
) extends InjectedController {

  var stopping = false
  life.addStopHook(() => {
    stopping = true
    Future.successful(Unit)
  })

  def gtg = Action {
    if (stopping)
      ServiceUnavailable("Shutting down")
    else
      Ok("\"OK\"")
  }

  def healthcheck = Action {
    Ok(Json.obj("data" -> JsArray(healthChecks.asScala.toSeq.sortBy(_.name).map(_.toJson))))
  }

}
