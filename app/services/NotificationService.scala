package services

import com.google.inject.ImplementedBy
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.mailer.Email
import play.api.mvc.Call
import services.tabula.ProfileService
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NotificationServiceImpl])
trait NotificationService {
  def newRegistration(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def registrationInvite(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def newEnquiry(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def enquiryReassign(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def newCaseOwner(newOwners: Set[Usercode], clientCase: Case)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def caseReassign(clientCase: Case)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def caseMessage(`case`: Case, client: UniversityID, sender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Activity]]
}

@Singleton
class NotificationServiceImpl @Inject()(
  myWarwickService: MyWarwickService,
  permissionService: PermissionService,
  groupService: GroupService,
  userLookupService: UserLookupService,
  emailService: EmailService,
  profileService: ProfileService,
  config: Configuration,
)(implicit executionContext: ExecutionContext) extends NotificationService {

  private lazy val domain: String = config.get[String]("domain")
  private lazy val initialTeam = Teams.fromId(config.get[String]("app.enquiries.initialTeamId"))

  override def newRegistration(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
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

  override def registrationInvite(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
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

  override def newEnquiry(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    withInitialTeamUsers { users =>
      val url = s"https://$domain${controllers.admin.routes.TeamEnquiryController.messages(enquiry.key.get).url}"

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

  override def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    if (sender == MessageSender.Client) {
      enquiryMessageToTeam(enquiry)
    } else {
      messageToClient(enquiry.universityID, enquiry.team, controllers.enquiries.routes.EnquiryMessagesController.messages(enquiry.key.get))
    }

  private def enquiryMessageToTeam(enquiry: Enquiry)(implicit t: TimingContext) = {
    withTeamUsers(enquiry.team) { users =>
      val url = s"https://$domain${controllers.admin.routes.TeamEnquiryController.messages(enquiry.key.get).url}"

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
  }

  private def messageToClient(client: UniversityID, team: Team, link: Call)(implicit t: TimingContext) = {
    withUser(client) { user =>
      val url = s"https://$domain${link.url}"

      emailService.queue(
        Email(
          subject = s"A message from the ${team.name} team has been received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.messagefromteam(user, team, url).toString.trim)
        ),
        Seq(user)
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set(user.usercode.string).asJava,
            Set[String]().asJava,
            s"The ${team.name} team has sent a message",
            url,
            null,
            "enquiry-message-from-team" // TODO change alert type
          )
          sendAndHandleResponse(activity)
      }
    }
  }

  override def caseMessage(c: Case, client: UniversityID, sender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    if (sender == MessageSender.Client)
      caseMessageToTeam(c)
    else {
      messageToClient(client, c.team, controllers.routes.IndexController.home()) // TODO no dedicated case message thread viewer atm; should there be?
    }

  private def caseMessageToTeam(c: Case)(implicit t: TimingContext) = {
    withTeamUsers(c.team) { users =>
      val url = s"https://$domain${controllers.admin.routes.CaseController.view(c.key.get).url}"

      emailService.queue(
        Email(
          subject = "Case Management: Enquiry message from client received",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.casemessagefromclient(url).toString.trim)
        ),
        users
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set[String]().asJava,
            Set(permissionService.webgroupFor(c.team).string).asJava,
            "Case message from client received",
            url,
            null,
            "case-message-from-client"
          )
          sendAndHandleResponse(activity)
      }
    }
  }

  override def enquiryReassign(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    withTeamUsers(enquiry.team) { users =>
      val url = s"https://$domain${controllers.admin.routes.TeamEnquiryController.messages(enquiry.key.get).url}"

      emailService.queue(
        Email(
          subject = "Case Management: Enquiry assigned",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.enquiryreassigned(url).toString.trim)
        ),
        users
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set[String]().asJava,
            Set(permissionService.webgroupFor(enquiry.team).string).asJava,
            "Enquiry assigned",
            url,
            null,
            "enquiry-assigned"
          )
          sendAndHandleResponse(activity)
      }
    }

  override def newCaseOwner(newOwners: Set[Usercode], clientCase: Case)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    if(newOwners.isEmpty) {
      Future.successful(Right(new Activity()))
    } else {
      withUsers(newOwners) { users =>
        val url = s"https://$domain${controllers.admin.routes.CaseController.view(clientCase.key.get).url}"

        emailService.queue(
          Email(
            subject = "Case Management: New case owner",
            from = "no-reply@warwick.ac.uk",
            bodyText = Some(views.txt.emails.newcaseowner(url).toString.trim)
          ),
          users.toSeq
        ).flatMap {
          case Left(errors) => Future.successful(Left(errors))
          case _ =>
            val activity = new Activity(
              newOwners.map(_.string).asJava,
              "New case owner",
              url,
              null,
              "case-owner-assigned"
            )
            sendAndHandleResponse(activity)
        }
      }
    }

  override def caseReassign(clientCase: Case)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    withTeamUsers(clientCase.team) { users =>
      val url = s"https://$domain${controllers.admin.routes.CaseController.view(clientCase.key.get).url}"

      emailService.queue(
        Email(
          subject = "Case Management: Case assigned",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some(views.txt.emails.casereassigned(url).toString.trim)
        ),
        users
      ).flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case _ =>
          val activity = new Activity(
            Set[String]().asJava,
            Set(permissionService.webgroupFor(clientCase.team).string).asJava,
            "Case assigned",
            url,
            null,
            "case-assigned"
          )
          sendAndHandleResponse(activity)
      }
    }

  private def sendAndHandleResponse(activity: Activity)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    FutureConverters.toScala(myWarwickService.sendAsNotification(activity)).map { resultList =>
      val results = resultList.asScala
      if (results.forall(_.getErrors.isEmpty)) {
        Right(activity)
      } else {
        Left(results.toList.filterNot(_.getErrors.isEmpty).map(response => ServiceError(
          response.getErrors.asScala.map(_.getMessage).mkString(", ")
        )))
      }
    }
  }

  private def withInitialTeamUsers(f: Seq[User] => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    withTeamUsers(initialTeam)(f)

  private def withTeamUsers(team: Team)(f: Seq[User] => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    val webGroup = permissionService.webgroupFor(team)
    groupService.getWebGroup(webGroup).fold(
      e => Future.successful(ServiceResults.exceptionToServiceResult(e)),
      r => r.map(group =>
        userLookupService.getUsers(group.members).fold(
          e => Future.successful(ServiceResults.exceptionToServiceResult(e)),
          userMap => f(userMap.values.toSeq)
        )
      ).getOrElse(
        Future.successful(Left(List(ServiceError(s"Cannot find webgroup with name ${webGroup.string}"))))
      )
    )
  }

  private def withUser(universityID: UniversityID)(f: User => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    profileService.getProfile(universityID).map(_.value).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      profile => profile.map(p => f(p.asUser)).getOrElse(
        Future.successful(Left(List(ServiceError(s"Cannot find user with university ID ${universityID.string}"))))
      )
    ))
  }

  private def withUsers(usercodes: Set[Usercode])(f: Set[User] => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    userLookupService.getUsers(usercodes.toSeq).fold(
      e => Future.successful(ServiceResults.exceptionToServiceResult(e)),
      userMap => {
        val (valid, invalid) = usercodes.partition(userMap.get(_).exists(_.isFound))
        if (invalid.nonEmpty) {
          Future.successful(Left(List(ServiceError(s"Cannot find users with usercodes: ${invalid.map(_.string).mkString(", ")}"))))
        } else {
          f(valid.map(userMap))
        }
      }
    )
  }

}
