package controllers.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import services.SecurityService
import services.tabula.ProfileService
import system.ImplicitRequestContext
import warwick.sso.{AuthenticatedRequest, UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidUniversityIDActionFilter @Inject()(
  securityService: SecurityService,
  userLookupService: UserLookupService,
  profileService: ProfileService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext {

  def ValidUniversityIDRequired(universityID: UniversityID): ActionFilter[AuthenticatedRequest] = new ActionFilter[AuthenticatedRequest] {
    override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: AuthenticatedRequest[A] = request

      userLookupService.getUsers(Seq(universityID)).toOption.flatMap(_.get(universityID)) match {
        case Some(user) if user.isFound => Future.successful(None)
        case _ => profileService.getProfile(universityID).map(_.value.fold(
          errors => Some(Results.BadRequest(views.html.errors.multiple(errors))),
          {
            case Some(_) => None
            case _ => Some(Results.NotFound(views.html.errors.notFound()))
          }
        ))
      }
    }

    override protected def executionContext: ExecutionContext = ec
  }

}
