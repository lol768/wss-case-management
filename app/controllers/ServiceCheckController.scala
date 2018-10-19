package controllers

import javax.inject.{Inject, Singleton}
import play.api.inject.ApplicationLifecycle
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class ServiceCheckController @Inject()(
  life: ApplicationLifecycle,
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

}
