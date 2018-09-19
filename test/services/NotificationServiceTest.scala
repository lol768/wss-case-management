package services

import java.time.LocalDate

import domain._
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import helpers.caching.CacheElement
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.mailer.Email
import services.tabula.ProfileService
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.response.Response
import warwick.sso.{Department, Group, GroupName, GroupService, UniversityID, User, UserLookupService, Usercode}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

class NotificationServiceTest extends PlaySpec with MockitoSugar with ScalaFutures with NoTimeTracking {

  private trait Fixture {
    val config = Configuration.from(Map(
      "domain" -> "wss.warwick.ac.uk",
      "app.enquiries.initialTeamId" -> "disability"
    ))

    val profileService = mock[ProfileService](RETURNS_SMART_NULLS)
    val emailService = mock[EmailService](RETURNS_SMART_NULLS)
    val myWarwickService = mock[MyWarwickService](RETURNS_SMART_NULLS)
    val groupService = mock[GroupService](RETURNS_SMART_NULLS)
    val userLookupService = mock[UserLookupService](RETURNS_SMART_NULLS)
    val permissionService = mock[PermissionService](RETURNS_SMART_NULLS)

    val notificationService = new NotificationServiceImpl(
      myWarwickService,
      permissionService,
      groupService,
      userLookupService,
      emailService,
      profileService,
      config
    )
  }

  "NotificationService" should {
    "send an email and a My Warwick notification on new registrations" in new Fixture {
      when(permissionService.webgroupFor(Teams.Disability)).thenReturn(GroupName("disability-team"))
      val group = Group(
        name = GroupName("disability-team"),
        title = Some("Disability team members"),
        members = Seq(Usercode("u000001"), Usercode("u0000002")),
        owners = Nil,
        `type` = "arbitrary",
        department = Department(None, Some("Wellbeing Services"), Some("WS")),
        updatedAt = JavaTime.offsetDateTime.atZoneSameInstant(JavaTime.timeZone),
        restricted = false
      )
      when(groupService.getWebGroup(GroupName("disability-team"))).thenReturn(Success(Some(group)))

      val user1 = User(new uk.ac.warwick.userlookup.User("u0000001"))
      val user2 = User(new uk.ac.warwick.userlookup.User("u0000002"))

      when(userLookupService.getUsers(group.members)).thenReturn(Success(Map(
        Usercode("u000001") -> user1,
        Usercode("u000002") -> user2,
      )))

      when(emailService.queue(any(), any())(any())).thenReturn(Future.successful(Right(Nil)))
      when(myWarwickService.sendAsNotification(any())).thenReturn(FutureConverters.toJava(Future.successful(List(
        new Response()
      ).asJava)).toCompletableFuture)

      val universityID = UniversityID("0672089")
      val activity = notificationService.newRegistration(universityID).futureValue.right.get

      activity.getRecipients.getUsers.asScala mustBe Set()
      activity.getRecipients.getGroups.asScala mustBe Set("disability-team")
      activity.getTitle mustBe "New registration received"
      activity.getText mustBe null
      activity.getUrl mustBe "https://wss.warwick.ac.uk/team/client/0672089"
      activity.getType mustBe "new-registration"

      val expectedEmail = Email(
        subject = "Case Management: New registration received",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some(
          """A new registration has been received: https://wss.warwick.ac.uk/team/client/0672089
            |
            |This email was sent from an automated system and replies to it will not reach a real person.""".stripMargin
        )
      )

      verify(emailService, times(1)).queue(expectedEmail, Seq(user1, user2))
      verify(myWarwickService, times(1)).sendAsNotification(activity)
      verifyNoMoreInteractions(emailService, myWarwickService)
    }

    "send an email and a My Warwick notification when a client is invited to register" in new Fixture {
      val universityID = UniversityID("0672089")
      val profile = SitsProfile(
        universityID = universityID,
        usercode = Usercode("u0672089"),
        fullName = "Mathew Mannion",
        dateOfBirth = LocalDate.of(1984, 8, 19),
        phoneNumber = None,
        warwickEmail = Some("m.mannion@warwick.ac.uk"),
        alternateEmail = None,
        address = None,
        residence = None,
        department = SitsDepartment("IN", "IT Services"),
        course = None,
        route = None,
        courseStatus = None,
        enrolmentStatus = None,
        attendance = None,
        group = None,
        yearOfStudy = None,
        startDate = None,
        endDate = None,
        nationality = None,
        dualNationality = None,
        tier4VisaRequired = None,
        disability = None,
        photo = None,
        personalTutors = Nil,
        researchSupervisors = Nil,
        userType = UserType.Staff
      )

      when(profileService.getProfile(universityID))
        .thenReturn(Future.successful(CacheElement(Right(Some(profile)), -1L, -1L, -1L)) : Future[CacheElement[ServiceResult[Option[SitsProfile]]]])

      when(emailService.queue(any(), any())(any())).thenReturn(Future.successful(Right(Nil)))
      when(myWarwickService.sendAsNotification(any())).thenReturn(FutureConverters.toJava(Future.successful(List(
        new Response()
      ).asJava)).toCompletableFuture)

      val activity = notificationService.registrationInvite(universityID).futureValue.right.get

      activity.getRecipients.getUsers.asScala mustBe Set("u0672089")
      activity.getRecipients.getGroups.asScala mustBe Set()
      activity.getTitle mustBe "Register for Wellbeing Support Services"
      activity.getText mustBe "You have been invited to register for Wellbeing Support Services"
      activity.getUrl mustBe "https://wss.warwick.ac.uk/register"
      activity.getType mustBe "registration-invite"

      val expectedEmail = Email(
        subject = "Register for Wellbeing Support Services",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some(
          """Dear Mathew,
            |
            |You have been invited to register for Wellbeing Support Services.
            |
            |Follow this link to complete your registration: https://wss.warwick.ac.uk/register
            |
            |This email was sent from an automated system and replies to it will not reach a real person.""".stripMargin
        )
      )

      verify(emailService, times(1)).queue(expectedEmail, Seq(profile.asUser))
      verify(myWarwickService, times(1)).sendAsNotification(activity)
      verifyNoMoreInteractions(emailService, myWarwickService)
    }
  }

}
