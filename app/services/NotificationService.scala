package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults
import helpers.ServiceResults.Implicits._
import helpers.ServiceResults.{ServiceError, ServiceResult}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.mailer.Email
import play.api.mvc.Call
import play.twirl.api.TxtFormat
import services.NotificationService._
import services.tabula.ProfileService
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

object NotificationService {
  type Domain = String
  
  implicit class DomainAwareUrlBuilder(val route: Call) {
    def build(implicit domain: Domain): String = route.absoluteURL(true, domain)
  }

  val fromAddress = "no-reply@warwick.ac.uk"
  val clientSubjectPrefix = "Wellbeing Support Services:"
  val teamSubjectPrefix = "Case Management:"

}

@ImplementedBy(classOf[NotificationServiceImpl])
trait NotificationService {
  def newRegistration(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def registrationInvite(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def newEnquiry(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def enquiryReassign(enquiry: Enquiry)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def newCaseOwner(newOwners: Set[Usercode], clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def caseReassign(clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def caseMessage(`case`: Case, client: UniversityID, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def newAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def cancelledAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def changedAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def appointmentConfirmation(appointment: Appointment, teamMembers: Set[Usercode], clientState: AppointmentState)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
  def appointmentReminder(appointment: Appointment, clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]]
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

  private implicit lazy val domain: NotificationService.Domain = config.get[String]("domain")
  private lazy val initialTeam = Teams.fromId(config.get[String]("app.enquiries.initialTeamId"))

  private lazy val myWarwickEnabled: Boolean = config.get[Boolean]("wellbeing.features.notifications.mywarwick")
  private lazy val emailEnabled: Boolean = config.get[Boolean]("wellbeing.features.notifications.email")

  override def newRegistration(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    withInitialTeamUsers { users =>
      val url = controllers.admin.routes.ClientController.client(universityID).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix New registration received",
        body = views.txt.emails.newregistration(url),
        recipients = users,
        activity = buildActivity(
          initialTeam,
          "New registration received",
          url,
          "new-registration"
        )
      )
    }
  }

  override def registrationInvite(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    val url = controllers.registration.routes.RegisterController.form().build
    withUser(universityID) { user =>
      queueEmailAndSendActivity(
        subject = "Register for Wellbeing Support Services",
        body = views.txt.emails.registrationinvite(user, url),
        recipients = Seq(user),
        activity = buildActivity(
          Set(user),
          "Register for Wellbeing Support Services",
          url,
          "registration-invite",
          "You have been invited to register for Wellbeing Support Services"
        )
      )
    }
  }

  override def newEnquiry(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    withInitialTeamUsers { users =>
      val url = controllers.admin.routes.TeamEnquiryController.messages(enquiryKey).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${enquiryKey.string} - New enquiry received",
        body = views.txt.emails.newenquiry(url),
        recipients = users,
        activity = buildActivity(
          initialTeam,
          "New enquiry received",
          url,
          "new-enquiry"
        )
      )
    }

  override def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    if (sender == MessageSender.Client) {
      enquiryMessageToTeam(enquiry)
    } else {
      messageToClient(enquiry.client.universityID, enquiry.team, enquiry.id.get)
    }

  private def enquiryMessageToTeam(enquiry: Enquiry)(implicit ac: AuditLogContext) = {
    withTeamUsers(enquiry.team) { users =>
      val url = controllers.admin.routes.TeamEnquiryController.messages(enquiry.key).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${enquiry.key.string} - Enquiry message from client received",
        body = views.txt.emails.enquirymessagefromclient(url),
        recipients = users,
        activity = buildActivity(
          enquiry.team,
          "Enquiry message from client received",
          url,
          "enquiry-message-from-client"
        )
      )
    }
  }

  private def messageToClient(client: UniversityID, team: Team, id: UUID)(implicit ac: AuditLogContext) = {
    withUser(client) { user =>
      val url = controllers.routes.ClientMessagesController.messages(id).build

      queueEmailAndSendActivity(
        subject = s"$clientSubjectPrefix A message from ${team.name} has been received",
        body = views.txt.emails.messagefromteam(user, team, url),
        recipients = Seq(user),
        activity = buildActivity(
          Set(user),
          s"${team.name} has sent a message",
          url,
          "message-from-team"
        )
      )
    }
  }

  override def caseMessage(c: Case, client: UniversityID, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    if (sender == MessageSender.Client)
      caseMessageToTeam(c)
    else {
      messageToClient(client, c.team, c.id.get)
    }

  private def caseMessageToTeam(c: Case)(implicit ac: AuditLogContext) = {
    withTeamUsers(c.team) { users =>
      val url = controllers.admin.routes.CaseController.view(c.key.get).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${c.key.get.string} - Case message from client received",
        body = views.txt.emails.casemessagefromclient(url),
        recipients = users,
        activity = buildActivity(
          c.team,
          "Case message from client received",
          url,
          "case-message-from-client"
        )
      )
    }
  }

  override def enquiryReassign(enquiry: Enquiry)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    withTeamUsers(enquiry.team) { users =>
      val url = controllers.admin.routes.TeamEnquiryController.messages(enquiry.key).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${enquiry.key.string} - Enquiry assigned",
        body = views.txt.emails.enquiryreassigned(url),
        recipients = users,
        activity = buildActivity(
          enquiry.team,
          "Enquiry assigned",
          url,
          "enquiry-assigned"
        )
      )
    }

  override def newCaseOwner(newOwners: Set[Usercode], clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    if(newOwners.isEmpty) {
      Future.successful(Right(new Activity()))
    } else {
      withUsers(newOwners) { users =>
        val url = controllers.admin.routes.CaseController.view(clientCase.key.get).build

        queueEmailAndSendActivity(
          subject = s"$teamSubjectPrefix ${clientCase.key.get.string} - New case owner",
          body = views.txt.emails.newcaseowner(url),
          recipients = users.toSeq,
          activity = buildActivity(
            users,
            "New case owner",
            url,
            "case-owner-assigned"
          )
        )
      }
    }

  override def caseReassign(clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    withTeamUsers(clientCase.team) { users =>
      val url = controllers.admin.routes.CaseController.view(clientCase.key.get).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${clientCase.key.get.string} - Case assigned",
        body = views.txt.emails.casereassigned(url),
        recipients = users,
        activity = buildActivity(
          clientCase.team,
          "Case assigned",
          url,
          "case-assigned"
        )
      )
    }

  override def newAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    withUsers(clients) { clientUsers =>
      val url = controllers.appointments.routes.AppointmentController.redirectToMyAppointments().build

      queueEmailAndSendActivity(
        subject = s"$clientSubjectPrefix New appointment created",
        body = views.txt.emails.newAppointment(url),
        recipients = clientUsers.toSeq,
        activity = buildActivity(
          clientUsers,
          "New appointment created",
          url,
          "appointment-created-message"
        )
      )
    }
  }

  override def cancelledAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    withUsers(clients) { clientUsers =>
      val url = controllers.appointments.routes.AppointmentController.redirectToMyAppointments().build

      queueEmailAndSendActivity(
        subject = s"$clientSubjectPrefix Appointment cancelled",
        body = views.txt.emails.cancelledAppointment(url),
        recipients = clientUsers.toSeq,
        activity = buildActivity(
          clientUsers,
          "Appointment cancelled",
          url,
          "appointment-cancelled-message"
        )
      )
    }
  }

  override def changedAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    withUsers(clients) { clientUsers =>
      val url = controllers.appointments.routes.AppointmentController.redirectToMyAppointments().build

      queueEmailAndSendActivity(
        subject = s"$clientSubjectPrefix Appointment updated",
        body = views.txt.emails.changedAppointment(url),
        recipients = clientUsers.toSeq,
        activity = buildActivity(
          clientUsers,
          "Appointment updated",
          url,
          "appointment-updated-message"
        )
      )
    }
  }

  override def appointmentConfirmation(appointment: Appointment, teamMembers: Set[Usercode], clientState: AppointmentState)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    withUsers(teamMembers) { teamMembers =>
      val url = controllers.admin.routes.AppointmentController.view(appointment.key).build

      queueEmailAndSendActivity(
        subject = s"$teamSubjectPrefix ${appointment.key.string} - Appointment ${clientState.clientDescription}",
        body = views.txt.emails.appointmentResponse(url, clientState.clientDescription.toLowerCase),
        recipients = teamMembers.toSeq,
        activity = buildActivity(
          teamMembers,
          s"Appointment ${clientState.clientDescription}",
          url,
          "appointment-confirmation-message"
        )
      )
    }
  }

  override def appointmentReminder(appointment: Appointment, clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] =
    withUsers(clients) { clientUsers =>
      val url = controllers.appointments.routes.AppointmentController.redirectToMyAppointments().build

      queueEmailAndSendActivity(
        subject = s"$clientSubjectPrefix Appointment reminder",
        body = views.txt.emails.appointmentReminder(appointment.start, url),
        recipients = clientUsers.toSeq,
        activity = buildActivity(
          clientUsers,
          s"Reminder: You have an appointment at ${JavaTime.Relative(appointment.start, printToday = false)}",
          url,
          "appointment-reminder-message"
        )
      )
    }

  private def queueEmailAndSendActivity(subject: String, body: TxtFormat.Appendable, recipients: Seq[User], activity: Activity)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = {
    val queueEmail =
      if (emailEnabled)
        emailService.queue(
          Email(
            subject = subject,
            from = fromAddress,
            bodyText = Some(body.toString.trim)
          ),
          recipients
        )
      else Future.successful(Right(Nil))

    queueEmail.successFlatMapTo(_ => sendAndHandleResponse(activity))
  }

  private def buildActivity(users: Set[User], title: String, url: String, activityType: String): Activity =
    buildActivity(users, title, url, activityType, null)

  private def buildActivity(users: Set[User], title: String, url: String, activityType: String, text: String): Activity =
    new Activity(
      users.map(_.usercode.string).asJava,
      Set[String]().asJava,
      title,
      url,
      text,
      activityType
    )

  private def buildActivity(team: Team, title: String, url: String, activityType: String): Activity =
    buildActivity(team, title, url, activityType, null)

  private def buildActivity(team: Team, title: String, url: String, activityType: String, text: String): Activity =
    new Activity(
      Set[String]().asJava,
      Set(permissionService.webgroupFor(team).string).asJava,
      title,
      url,
      text,
      activityType
    )

  private def sendAndHandleResponse(activity: Activity)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    if (myWarwickEnabled)
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
    else Future.successful(Right(activity))

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


  // DummyImplicit crime - otherwise this clashes with the usercode version due to type erasure - suspect that more idomatic TypeTag trickery to solve this problem may exist
  private def withUsers(universityIDs: Set[UniversityID])(f: Set[User] => Future[ServiceResult[Activity]])(implicit t: TimingContext, d: DummyImplicit): Future[ServiceResult[Activity]] = {
    profileService.getProfiles(universityIDs).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      profiles => {
        val users = universityIDs.flatMap(uid =>
          profiles.get(uid).map(_.asUser)
            .orElse(userLookupService.getUsers(Seq(uid)).toOption.flatMap(_.get(uid)))
        )

        f(users)
      }
    ))
  }

  private def withUser(universityID: UniversityID)(f: User => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    profileService.getProfile(universityID).map(_.value).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      profile =>
        profile.map(_.asUser).orElse(
          userLookupService.getUsers(Seq(universityID)).toOption.flatMap(_.get(universityID))
        ).map(f.apply).getOrElse(
          Future.successful(Left(List(ServiceError(s"Cannot find user with university ID ${universityID.string}"))))
        )
    ))
  }

  private def withUser(usercode: Usercode)(f: User => Future[ServiceResult[Activity]])(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    withUsers(Set(usercode)){ users => f(users.head) }
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
