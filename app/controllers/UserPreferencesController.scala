package controllers

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, UserPreferencesService}

import scala.concurrent.{ExecutionContext, Future}
import UserPreferencesController._
import domain.UserPreferences
import play.api.libs.json.{JsValue, Json}

object UserPreferencesController {
  val calendarViewForm = Form(single("calendarView" -> nonEmptyText))
}

@Singleton
class UserPreferencesController @Inject()(
  securityService: SecurityService,
  userPreferences: UserPreferencesService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._

  def calendarView: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    calendarViewForm.bindFromRequest().fold(
      formWithErrors => Future.successful(
        BadRequest(Json.toJson(API.Failure[JsValue](
          status = "bad-request",
          errors = formWithErrors.errors.map { e => API.Error(e.key, e.message) }
        )))
      ),
      calendarView =>
        userPreferences.get(currentUser().usercode).successFlatMap { prefs =>
          def result(p: UserPreferences) =
            Ok(Json.toJson(API.Success(data = Json.toJson(p)(UserPreferences.formatter))))

          // Short-circuit
          if (prefs.calendarView == calendarView)
            Future.successful(result(prefs))
          else
            userPreferences.update(currentUser().usercode, prefs.copy(calendarView = calendarView))
              .successMap(result)
        }
    )
  }
}
