package services

import com.google.inject.ImplementedBy
import domain._
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
  def registrationInvite(universityID: UniversityID): Future[ServiceResult[Activity]]
  def newEnquiry(enquiry: Enquiry): Future[ServiceResult[Activity]]
  def enquiryMessage(enquiry: Enquiry, message: Message): Future[ServiceResult[Activity]]
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

  override def newRegistration(universityID: UniversityID): Future[ServiceResult[Activity]] = {
    withInitialTeamUsers { users =>
      val url = s"https://$domain${controllers.admin.routes.ClientController.client(universityID).url}"

      emailService.queue(
        Email(
          subject = "Case Management: New registration received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.newregistration(url).toString.trim)
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

  override def registrationInvite(universityID: UniversityID): Future[ServiceResult[Activity]] = {
    val url = s"https://$domain${controllers.registration.routes.RegisterController.form().url}"
    withUser(universityID) { user =>
      emailService.queue(
        Email(
          subject = "Register for Wellbeing Support Services",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.registrationinvite(user, url).toString.trim)
        ),
        Seq(user)
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set(user.usercode.string).asJava,
            "Register for Wellbeing Support Services",
            url,
            "You have been invited to register for Wellbeing Support Services",
            "registration-invite"
          )
          sendAndHandleResponse(activity)
      }
    }
  }

  override def newEnquiry(enquiry: Enquiry): Future[ServiceResult[Activity]] =
    withInitialTeamUsers { users =>
      val url = s"https://$domain${controllers.enquiries.routes.EnquiryMessagesController.messages(enquiry.id.get).url}"

      emailService.queue(
        Email(
          subject = "Case Management: New enquiry received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.newenquiry(url).toString.trim)
        ),
        users
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set[String]().asJava,
            Set(permissionService.webgroupFor(initialTeam).string).asJava,
            "New enquiry received",
            url,
            null,
            "new-enquiry"
          )
          sendAndHandleResponse(activity)
      }
    }

  override def enquiryMessage(enquiry: Enquiry, message: Message): Future[ServiceResult[Activity]] =
    if (message.sender == MessageSender.Client) {
      withTeamUsers(enquiry.team) { users =>
        val url = s"https://$domain${controllers.enquiries.routes.EnquiryMessagesController.messages(enquiry.id.get).url}"

        emailService.queue(
          Email(
            subject = "Case Management: Enquiry message from client received",
            from = "no-reply@warwick.ac.uk",
            bodyText = Some(views.txt.emails.enquirymessagefromclient(url).toString.trim)
          ),
          users
        ).flatMap {
          case Left(errors) => Future.successful(Left(errors))
          case _ =>
            val activity = new Activity(
              Set[String]().asJava,
              Set(permissionService.webgroupFor(enquiry.team).string).asJava,
              "Enquiry message from client received",
              url,
              null,
              "enquiry-message-from-client"
            )
            sendAndHandleResponse(activity)
        }
      }
    } else {
      withUser(enquiry.universityID) { user =>
        val url = s"https://$domain${controllers.enquiries.routes.EnquiryMessagesController.messages(enquiry.id.get).url}"

        emailService.queue(
          Email(
            subject = s"A message from the ${enquiry.team.name} team has been received",
            from = "no-reply@warwick.ac.uk",
            bodyText = Some(views.txt.emails.enquirymessagefromteam(user, enquiry.team, url).toString.trim)
          ),
          Seq(user)
        ).flatMap {
          case Left(errors) => Future.successful(Left(errors))
          case _ =>
            val activity = new Activity(
              Set(user.usercode.string).asJava,
              Set[String]().asJava,
              s"The ${enquiry.team.name} team has replied to your enquiry",
              url,
              null,
              "enquiry-message-from-team"
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

  private def withInitialTeamUsers(f: Seq[User] => Future[ServiceResult[Activity]]): Future[ServiceResult[Activity]] =
    withTeamUsers(initialTeam)(f)

  private def withTeamUsers(team: Team)(f: Seq[User] => Future[ServiceResult[Activity]]): Future[ServiceResult[Activity]] = {
    val webGroup = permissionService.webgroupFor(team)
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
          override def message: String = s"Cannot find webgroup with name ${webGroup.string}"
        })))
      )
    )
  }

  private def withUser(universityID: UniversityID)(f: User => Future[ServiceResult[Activity]]): Future[ServiceResult[Activity]] = {
    userLookupService.getUsers(Seq(universityID)).fold(
      e => Future.successful(Left(List(new ServiceError {
        override def message: String = e.getMessage
        override def cause = Some(e)
      }))),
      userMap => userMap.get(universityID).map(f).getOrElse(
        Future.successful(Left(List(new ServiceError {
          override def message: String = s"Cannot find user with university ID ${universityID.string}"
        })))
      )
    )
  }

}
