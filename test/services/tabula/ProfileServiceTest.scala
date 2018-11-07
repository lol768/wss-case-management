package services.tabula

import java.time.LocalDate

import domain._
import helpers.OneAppPerSuite
import helpers.caching.NeverExpiringMemoryAsyncCacheApi
import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.PlaySpec
import play.api.http.DefaultFileMimeTypesProvider
import play.api.http.HttpConfiguration.HttpConfigurationProvider
import play.api.mvc._
import play.api.routing.sird._
import play.api.test.WsTestClient
import play.api.{Configuration, Environment}
import play.core.server.Server
import services.{NoAuditLogging, NullTimingService, PhotoServiceImpl}
import uk.ac.warwick.sso.client.trusted.TrustedApplication.HEADER_ERROR_CODE
import uk.ac.warwick.sso.client.trusted.{CurrentApplication, EncryptedCertificate, TrustedApplicationsManager}
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

class ProfileServiceTest extends PlaySpec with OneAppPerSuite with MockitoSugar with ScalaFutures with NoAuditLogging {

  implicit val ec: ExecutionContext = get[ExecutionContext]
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(2000, Millis)), scaled(Span(15, Millis)))
  private val env = Environment.simple()
  private val httpConfiguration = new HttpConfigurationProvider(Configuration.load(env), env).get
  private val fileMimeTypes = new DefaultFileMimeTypesProvider(httpConfiguration.fileMimeTypes).get

  def withProfileService[T](trustedAppFail: Boolean = false)(block: ProfileService => T): T = {
    Server.withRouterFromComponents() { components =>
      import components.{defaultActionBuilder => Action}
      {
        case GET(p"/test-profile/$universityID") => Action {
          val result = Results.Ok.sendResource(s"services/tabula/$universityID.json")(fileMimeTypes)
          if (trustedAppFail) result.withHeaders(HEADER_ERROR_CODE -> "herons in the pipes")
          else result
        }
      }
    }{ implicit port =>
      WsTestClient.withClient { client =>
        val trustedApplicationsManager = mock[TrustedApplicationsManager]
        val currentApp = mock[CurrentApplication]
        val cert = mock[EncryptedCertificate]
        when(currentApp.encode(Matchers.any(), Matchers.any())).thenReturn(cert)
        when(trustedApplicationsManager.getCurrentApplication).thenReturn(currentApp)
        val config = Configuration.from(Map(
          "wellbeing.tabula.user" -> "heron-user",
          "wellbeing.tabula.profile" -> "/test-profile",
          "wellbeing.photos.host" -> "photos.warwick.ac.uk",
          "wellbeing.photos.appname" -> "heron",
          "wellbeing.photos.key" -> "heron-key"
        ))
        val cache = new NeverExpiringMemoryAsyncCacheApi()
        val profileService = new ProfileServiceImpl(
          client,
          trustedApplicationsManager,
          cache,
          new PhotoServiceImpl(config),
          config,
          new NullTimingService
        )


        block(profileService)
      }
    }
  }

  "ProfileService" should {
    "fetch a student profile" in  withProfileService() { profileService =>
      val studentProfile = profileService.getProfile(UniversityID("1234567")).futureValue.value.right.get.get
      studentProfile.universityID mustBe UniversityID("1234567")
      studentProfile.fullName mustBe "Reiher Gwenyn"
      studentProfile.dateOfBirth mustBe LocalDate.parse("1995-08-23")
      studentProfile.phoneNumber mustBe Some("07743766700")
      studentProfile.warwickEmail mustBe Some("Reiher.Gwenyn@warwick.ac.uk")
      studentProfile.alternateEmail mustBe Some("Reiher.Gwenyn@warwick.ac.uk")
      studentProfile.address mustBe Some(Address(Some("HB666"), Some("Heronbank"), Some("University of Warwick"), Some("Coventry"), None, Some("CV4 7ES")))
      studentProfile.residence mustBe Some(Residence.Heronbank)
      studentProfile.department mustBe SitsDepartment("lf", "Life Sciences")
      studentProfile.course mustBe Some(Course("RBSA-C1PH","RBSA-C1PH Ardea Herodias Studies"))
      studentProfile.route mustBe Some(Route("c1ph","C1PH Ardea Herodias Studies"))
      studentProfile.courseStatus mustBe Some(CourseStatus("C","Current Student"))
      studentProfile.enrolmentStatus mustBe Some(EnrolmentStatus("2","Not yet re-enrolled: continuing student"))
      studentProfile.attendance mustBe Some(Attendance.FullTime)
      studentProfile.group mustBe Some(StudentGroup.PGR)
      studentProfile.yearOfStudy mustBe Some(YearOfStudy(4, "M2"))
      studentProfile.startDate mustBe Some(LocalDate.parse("2015-10-05"))
      studentProfile.endDate mustBe Some(LocalDate.parse("2019-09-30"))
      studentProfile.nationality mustBe Some("British")
      studentProfile.dualNationality mustBe None
      studentProfile.tier4VisaRequired mustBe Some(false)
      studentProfile.disability mustBe Some(SitsDisability("I","Unclassified disability","Condition not listed"))
      studentProfile.disabilityFundingStatus mustBe Some(SitsDisabilityFundingStatus("5", "Not in receipt of DSA"))
      studentProfile.jobTitle mustBe None
      studentProfile.photo mustBe Some("https://photos.warwick.ac.uk/heron/photo/842cfad8a90147436f7a7dfeb2d42800/1234567")
      studentProfile.userType mustBe UserType.Student
      studentProfile.personalTutors mustBe Nil
      studentProfile.researchSupervisors.size mustBe 1

      val supervisorProfile = studentProfile.researchSupervisors.head
      supervisorProfile.universityID mustBe UniversityID("1222222")
      supervisorProfile.fullName mustBe "Garza Hegre"
      supervisorProfile.dateOfBirth mustBe LocalDate.parse("1972-03-14")
      supervisorProfile.warwickEmail mustBe Some("garza.hegre@warwick.ac.uk")
      supervisorProfile.department mustBe SitsDepartment("lf", "Life Sciences")
      supervisorProfile.photo mustBe Some("https://photos.warwick.ac.uk/heron/photo/d48375298d78b79384e2ff529e2aac62/1222222")
      supervisorProfile.userType mustBe UserType.Staff
    }

    "fetch an applicant profile" in  withProfileService() { profileService =>
      val applicantProfile = profileService.getProfile(UniversityID("1812345")).futureValue.value.right.get.get
      applicantProfile.universityID mustBe UniversityID("1812345")
      applicantProfile.fullName mustBe "Reynard Fox"
      applicantProfile.dateOfBirth mustBe LocalDate.parse("1996-02-08")
      applicantProfile.phoneNumber mustBe Some("07512345678")
      applicantProfile.warwickEmail mustBe Some("reynard.fox@outlook.com")
      applicantProfile.alternateEmail mustBe Some("reynard.fox@outlook.com")
      applicantProfile.address mustBe Some(Address(Some("Heron Tower"), Some("110 Bishopsgate"), None, Some("London"), None, Some("EC2N 4AY")))
      applicantProfile.residence mustBe None
      applicantProfile.department mustBe SitsDepartment("sl", "Student Recruitment")
      applicantProfile.course mustBe None
      applicantProfile.route mustBe None
      applicantProfile.courseStatus mustBe None
      applicantProfile.enrolmentStatus mustBe None
      applicantProfile.attendance mustBe None
      applicantProfile.group mustBe None
      applicantProfile.yearOfStudy mustBe None
      applicantProfile.startDate mustBe None
      applicantProfile.endDate mustBe None
      applicantProfile.nationality mustBe Some("British")
      applicantProfile.dualNationality mustBe Some("Zimbabwean")
      applicantProfile.tier4VisaRequired mustBe None
      applicantProfile.disability mustBe Some(SitsDisability("E","Long standing condition","You have a long standing illness or condition"))
      applicantProfile.disabilityFundingStatus mustBe Some(SitsDisabilityFundingStatus("9", "Pending"))
      applicantProfile.jobTitle mustBe Some("Applicant")
      applicantProfile.photo mustBe Some("https://photos.warwick.ac.uk/heron/photo/3bba35c1a0c436780cefa97fecac9c5e/1812345")
      applicantProfile.userType mustBe UserType.Applicant
    }

    "fetch a staff profile" in withProfileService() { profileService =>
      val staffProfile = profileService.getProfile(UniversityID("1170836")).futureValue.value.right.get.get
      staffProfile.universityID mustBe UniversityID("1170836")
      staffProfile.fullName mustBe "Ritchie Allen"
      staffProfile.dateOfBirth mustBe LocalDate.parse("1986-08-23")
      staffProfile.phoneNumber mustBe None
      staffProfile.warwickEmail mustBe Some("Ritchie.Allen@warwick.ac.uk")
      staffProfile.alternateEmail mustBe None
      staffProfile.address mustBe None
      staffProfile.residence mustBe None
      staffProfile.department mustBe SitsDepartment("in", "IT Services")
      staffProfile.course mustBe None
      staffProfile.route mustBe None
      staffProfile.courseStatus mustBe None
      staffProfile.enrolmentStatus mustBe None
      staffProfile.attendance mustBe None
      staffProfile.group mustBe None
      staffProfile.yearOfStudy mustBe None
      staffProfile.startDate mustBe None
      staffProfile.endDate mustBe None
      staffProfile.nationality mustBe None
      staffProfile.dualNationality mustBe None
      staffProfile.tier4VisaRequired mustBe None
      staffProfile.disability mustBe None
      staffProfile.disabilityFundingStatus mustBe None
      staffProfile.jobTitle mustBe Some("Senior Web Developer")
      staffProfile.photo mustBe Some("https://photos.warwick.ac.uk/heron/photo/d462b29afd79000c21bcea89ecea9d71/1170836")
      staffProfile.userType mustBe UserType.Staff
    }

    "handle missing profiles" in withProfileService() { profileService =>
      val errors = profileService.getProfile(UniversityID("7654321")).futureValue.value.left.get
      errors.head.message must include("Tabula API response not successful")
      errors.head.message must include("Item not found")
    }

    "handle permission errors with the API user" in withProfileService() { profileService =>
      val errors = profileService.getProfile(UniversityID("1111111")).futureValue.value.left.get
      errors.head.message must include("Tabula API response not successful")
      errors.head.message must include("unauthorized")
    }

    "handle integration errors" in withProfileService(trustedAppFail = true) { profileService =>
      val errors = profileService.getProfile(UniversityID("1111111")).futureValue.value.left.get
      errors.head.message must include("Trusted apps integration error")
    }

  }

}
