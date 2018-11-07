package services.office365

import java.util.UUID

import domain.Owner.EntityType
import domain.{AppointmentTeamMember, Member, Owner}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.quartz._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json.Json
import services.job.UpdateAppointmentInOffice365Job
import services.{NoAuditLogging, UpdateDifferencesResult}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext.Implicits.global

class Office365CalendarServiceTest extends PlaySpec with MockitoSugar with ScalaFutures with NoAuditLogging with BeforeAndAfter {

  private val mockConfig = mock[Configuration](RETURNS_SMART_NULLS)
  when(mockConfig.get[Boolean]("wellbeing.features.pushAppointmentsToOffice365")).thenReturn(true)

  private val mockScheduler = mock[Scheduler](RETURNS_SMART_NULLS)

  private val service = new Office365CalendarServiceImpl(mockScheduler, mockConfig)

  private val appointmentId = UUID.randomUUID()

  private val member1 = Member(Usercode("abc"), None, JavaTime.offsetDateTime)
  private val member2 = Member(Usercode("bcd"), None, JavaTime.offsetDateTime)
  private val member3 = Member(Usercode("cde"), None, JavaTime.offsetDateTime)

  private val jobKey = new JobKey(appointmentId.toString, "UpdateAppointmentInOffice365")

  "Office365CalendarService" should {

    "schedule a new job" in {
      var jobDetail: JobDetail = null

      when(mockScheduler.scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())).thenAnswer((invocation: InvocationOnMock) => {
        jobDetail = invocation.getArguments.apply(0).asInstanceOf[JobDetail]
        null
      })

      service.updateAppointment(appointmentId, Set(
        AppointmentTeamMember(member1, None),
        AppointmentTeamMember(member2, None)
      ))

      jobDetail.getKey.getName mustBe appointmentId.toString
      val dataMap = jobDetail.getJobDataMap
      dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId) mustBe appointmentId.toString
      val ownersJson = Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners))
      val ownersMap = ownersJson.as[Map[String, String]]
      ownersMap.keySet.size mustBe 2
      ownersMap(member1.usercode.string) mustBe ""
      ownersMap(member2.usercode.string) mustBe ""
    }

    "update an existing job" in {
      var jobDetail: JobDetail = null

      val existingJob = JobBuilder.newJob(classOf[UpdateAppointmentInOffice365Job])
        .withIdentity(jobKey)
        .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId, appointmentId.toString)
        .usingJobData(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners, Json.stringify(Json.toJson(Map(
          member3.usercode.string -> "some-id"
        ))))
        .build()

      when(mockScheduler.checkExists(jobKey)).thenReturn(true)
      when(mockScheduler.getJobDetail(jobKey)).thenReturn(existingJob)

      when(mockScheduler.scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())).thenAnswer((invocation: InvocationOnMock) => {
        jobDetail = invocation.getArguments.apply(0).asInstanceOf[JobDetail]
        null
      })

      service.updateAppointment(appointmentId, Set(
        AppointmentTeamMember(member1, None),
        AppointmentTeamMember(member2, None)
      ))

      verify(mockScheduler, times(1)).deleteJob(jobKey)

      jobDetail.getKey.getName mustBe appointmentId.toString
      val dataMap = jobDetail.getJobDataMap
      dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId) mustBe appointmentId.toString
      val ownersJson = Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners))
      val ownersMap = ownersJson.as[Map[String, String]]
      ownersMap.keySet.size mustBe 3
      ownersMap(member1.usercode.string) mustBe ""
      ownersMap(member2.usercode.string) mustBe ""
      ownersMap(member3.usercode.string) mustBe "some-id"
    }

    "handle both method signatures" in {
      var jobDetail: JobDetail = null

      when(mockScheduler.scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())).thenAnswer((invocation: InvocationOnMock) => {
        jobDetail = invocation.getArguments.apply(0).asInstanceOf[JobDetail]
        null
      })

      service.updateAppointment(appointmentId, UpdateDifferencesResult[Owner](
        added = Seq(Owner(appointmentId, EntityType.Appointment, member1.usercode, None, JavaTime.offsetDateTime)),
        removed = Seq(Owner(appointmentId, EntityType.Appointment, member2.usercode, Some("some-id"), JavaTime.offsetDateTime)),
        unchanged = Seq(Owner(appointmentId, EntityType.Appointment, member3.usercode, Some("some-other-id"), JavaTime.offsetDateTime))
      ))

      jobDetail.getKey.getName mustBe appointmentId.toString
      val dataMap = jobDetail.getJobDataMap
      dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId) mustBe appointmentId.toString
      val ownersJson = Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners))
      val ownersMap = ownersJson.as[Map[String, String]]
      ownersMap.keySet.size mustBe 3
      ownersMap(member1.usercode.string) mustBe ""
      ownersMap(member2.usercode.string) mustBe "some-id"
      ownersMap(member3.usercode.string) mustBe "some-other-id"
    }

  }

  after {
    reset(mockScheduler)
  }

}
