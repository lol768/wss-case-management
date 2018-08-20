package services

import com.google.inject.ImplementedBy
import domain.Teams
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.mailer.Email
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NotificationServiceImpl])
trait NotificationService {

  def newRegistration(universityID: UniversityID): Future[ServiceResult[Activity]]

}

class NotificationServiceImpl @Inject()(
  myWarwickService: MyWarwickService,
  permissionService: PermissionService,
  groupService: GroupService,
  userLookupService: UserLookupService,
  emailService: EmailService,
  config: Configuration
)(implicit executionContext: ExecutionContext) extends NotificationService {

  private lazy val domain: String = config.get[String]("domain")
  private lazy val initialTeam = Teams.fromId(config.get[String]("app.enquiries.initialTeamId"))

  def newRegistration(universityID: UniversityID): Future[ServiceResult[Activity]] = {
    withInitialTeamUsers { users =>
      val url = s"https://$domain${controllers.admin.routes.ClientController.client(universityID).url}"

      emailService.queue(
        Email(
          subject = "Case Management: New registration received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.newregistration(url).toString)
        ),
        users
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set[String]().asJava,
            Set(permissionService.webgroupFor(initialTeam).string).asJava,
            "New registration received",
            url,
            null,
            "new-registration"
          )
          sendAndHandleResponse(activity)
      }
    }
  }

  private def sendAndHandleResponse(activity: Activity): Future[ServiceResult[Activity]] = {
    FutureConverters.toScala(myWarwickService.sendAsNotification(activity)).map { resultList =>
      val results = resultList.asScala
      if (results.forall(_.getErrors.isEmpty)) {
        Right(activity)
      } else  {
        Left(results.toList.filterNot(_.getErrors.isEmpty).map(response => new ServiceError {
          override def message: String = response.getErrors.asScala.map(_.getMessage).mkString(", ")
        }))
      }
    }
  }

  private def withInitialTeamUsers(f: Seq[User] => Future[ServiceResult[Activity]]): Future[ServiceResult[Activity]] = {
    val webGroup = permissionService.webgroupFor(initialTeam)
    groupService.getWebGroup(webGroup).fold(
      e => Future.successful(Left(List(new ServiceError {
        override def message: String = e.getMessage
        override def cause = Some(e)
      }))),
      r => r.map(group =>
        userLookupService.getUsers(group.members).fold(
          e => Future.successful(Left(List(new ServiceError {
            override def message: String = e.getMessage
            override def cause = Some(e)
          }))),
          userMap => f(userMap.values.toSeq)
        )
      ).getOrElse(
        Future.successful(Left(List(new ServiceError {
          override def message: String = s"Cannot fnd webgroup with name ${webGroup.string}"
        })))
      )
    )
  }

}
