package controllers

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, UserPreferencesService}

import scala.concurrent.{ExecutionContext, Future}
import UserPreferencesController._
import controllers.refiners.AnyTeamActionRefiner
import domain.UserPreferences
import play.api.i18n.Messages
import play.api.libs.json.{JsValue, Json}

object UserPreferencesController {
  val calendarViewForm = Form(single("calendarView" -> nonEmptyText))

  val preferencesForm = Form(
    single("office365Enabled" -> boolean)
  )
}

@Singleton
class UserPreferencesController @Inject()(
  securityService: SecurityService,
  userPreferences: UserPreferencesService,
  anyTeamActionRefiner: AnyTeamActionRefiner
)(implicit executionContext: ExecutionContext) extends BaseController {
  import securityService._
  import anyTeamActionRefiner._

  def form: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    userPreferences.get(currentUser().usercode).successMap(prefs =>
      Ok(views.html.preferences(UserPreferencesController.preferencesForm.fill(prefs.office365Enabled)))
    )
  }

  def submit: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    UserPreferencesController.preferencesForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(views.html.preferences(formWithErrors))),
      enabled =>
        userPreferences.get(currentUser().usercode).successFlatMap { prefs =>
          userPreferences.update(currentUser().usercode, prefs.copy(office365Enabled = enabled)).successMap(_ =>
            Redirect(routes.IndexController.home()).flashing("success" -> Messages("flash.userPreferences.updated"))
          )
        }
    )
  }

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
