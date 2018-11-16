package services

import java.time
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

import domain.Owner.EntityType
import domain.Teams.WellbeingSupport
import domain._
import helpers.ServiceResults
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.quartz._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, Json}
import services.job.UpdateAppointmentInOffice365Job
import warwick.office365.O365Service
import warwick.sso.{UniversityID, Usercode}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdateAppointmentInOffice365JobTest extends PlaySpec with MockitoSugar with ScalaFutures with NoAuditLogging with BeforeAndAfter {

  trait Fixture {
    val mockConfig: Configuration = mock[Configuration](RETURNS_SMART_NULLS)
    when(mockConfig.get[String]("domain")).thenReturn("")

    val mockScheduler: Scheduler = mock[Scheduler](RETURNS_SMART_NULLS)
    val mockAppointmentService: AppointmentService = mock[AppointmentService](RETURNS_SMART_NULLS)
    val mockOwnerService: OwnerService = mock[OwnerService](RETURNS_SMART_NULLS)
    val mockO365Service: O365Service = mock[O365Service](RETURNS_SMART_NULLS)
    val mockPreferencesService: UserPreferencesService = mock[UserPreferencesService](RETURNS_SMART_NULLS)

    when(mockPreferencesService.get(Matchers.any[Set[Usercode]])(Matchers.any())).thenAnswer((invocation: InvocationOnMock) => {
      val usercodes = invocation.getArguments.apply(0).asInstanceOf[Set[Usercode]]
      Future.successful(Right(usercodes.map(u => u -> UserPreferences.default.copy(office365Enabled = true)).toMap))
    })

    val appointmentId: UUID = UUID.randomUUID()
    val appointment: Appointment = Appointment(
      id = appointmentId,
      key = IssueKey.apply(IssueKeyType.Appointment, 1),
      start = OffsetDateTime.of(2018, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
      duration = time.Duration.ofHours(1),
      team = WellbeingSupport,
      appointmentType = AppointmentType.FaceToFace,
      purpose = AppointmentPurpose.InitialAssessment,
      state = AppointmentState.Accepted,
      cancellationReason = None,
      outcome = Set(),
      created = null,
      lastUpdated = null
    )
    val appointmentRender: AppointmentRender = mock[AppointmentRender](RETURNS_SMART_NULLS)
    when(appointmentRender.appointment).thenReturn(appointment)
    when(appointmentRender.clients).thenReturn(Set(AppointmentClient(Client(UniversityID("1234"), None, null), AppointmentState.Accepted, None, None)))
    when(appointmentRender.room).thenReturn(Some(Room(null, Building(null, "Building", 0, null, null), "Room", 0, available = true, null, null)))

    when(mockAppointmentService.findFull(Matchers.any())(Matchers.any())).thenReturn(Future.successful(
      ServiceResults.error[AppointmentRender](s"Could not find an Appointment with ID")
    ))
    when(mockAppointmentService.findFull(appointmentId)).thenReturn(Future.successful(Right(appointmentRender)))

    val expectedAppointmentJson: JsObject = Json.obj(
      "Subject" -> "Wellbeing Support Service appointment (APP-001)",
      "Body" -> Json.obj(
        "ContentType" -> "Text",
        "Content" -> "You have a face to face appointment with 1 client"
      ),
      "Start" -> Json.obj(
        "DateTime" -> "2018-01-01T12:00:00.000",
        "TimeZone" -> "Europe/London"
      ),
      "End" -> Json.obj(
        "DateTime" -> "2018-01-01T13:00:00.000",
        "TimeZone" -> "Europe/London"
      ),
      "WebLink" -> "https:///team/appointment/APP-001",
      "Location" -> Json.obj(
        "DisplayName" -> "Room, Building",
        "Address" -> JsNull
      )
    )

    val job = new UpdateAppointmentInOffice365Job(
      mockScheduler,
      mockAppointmentService,
      mockOwnerService,
      mockO365Service,
      mockPreferencesService,
      mockConfig
    )

  }

  private def setupContext(dataMap: Map[String, String]): JobExecutionContext = {
    val jobDataMap = new JobDataMap(dataMap.asJava)
    val mockJobDetail = mock[JobDetail](RETURNS_SMART_NULLS)
    val mockContext = mock[JobExecutionContext](RETURNS_SMART_NULLS)
    when(mockContext.getJobDetail).thenReturn(mockJobDetail)
    when(mockJobDetail.getJobDataMap).thenReturn(jobDataMap)
    mockContext
  }

  "UpdateAppointmentInOffice365Job" should {

    "handle wrong appointment ID (should throw, no rescheduling)" in new Fixture {
      private val context = setupContext(Map(
        "appointmentId" -> UUID.randomUUID().toString,
        "owners" -> "{}"
      ))

      private val exception = intercept[RuntimeException] {
        job.execute(context)
      }
      exception.getMessage.indexOf("Could not find an Appointment with ID") mustBe 0
      verify(mockScheduler, never()).scheduleJob(Matchers.any())
    }

    "handle cancelled appointment (delete all O365 events with an ID)" in new Fixture {
      private val cancelledAppointmentId = UUID.randomUUID()
      val outlookId1 = "12345"
      val outlookId2 = "23456"
      private val cancelledAppointment = mock[Appointment](RETURNS_SMART_NULLS)
      when(cancelledAppointment.id).thenReturn(cancelledAppointmentId)
      when(cancelledAppointment.state).thenReturn(AppointmentState.Cancelled)
      private val cancelledRender = mock[AppointmentRender](RETURNS_SMART_NULLS)
      when(cancelledRender.appointment).thenReturn(cancelledAppointment)

      when(mockAppointmentService.findFull(Matchers.eq(cancelledAppointmentId))(Matchers.any())).thenReturn(Future.successful(Right(cancelledRender)))
      when(mockO365Service.deleteO365(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(JsNull)))

      private val context = setupContext(Map(
        "appointmentId" -> cancelledAppointmentId.toString,
        "owners" -> s"""{"cusfal":"$outlookId1", "cusebr":"$outlookId2", "cuscao":""}"""
      ))

      job.execute(context)
      verify(mockO365Service, times(1)).deleteO365("cusfal", s"events/$outlookId1", Json.obj())
      verify(mockO365Service, times(1)).deleteO365("cusebr", s"events/$outlookId2", Json.obj())
      verify(mockO365Service, never()).deleteO365(Matchers.eq("cuscao"), Matchers.any(), Matchers.any())
      verify(mockScheduler, never()).scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())
    }

    "handle appointment changes (1 owner added, 1 owner unchanged, 2 owners removed (1 previously pushed))" in new Fixture {
      val owner1 = "cusfal"
      val owner1outlookId = ""
      val owner2 = "cusebr"
      val owner2outlookId = "1234"
      val owner3 = "cuscao"
      val owner3outlookId = "2345"
      val owner4 = "curef"
      val owner4outlookId = ""
      private val context = setupContext(Map(
        "appointmentId" -> appointmentId.toString,
        "owners" -> s"""{"$owner1":"$owner1outlookId", "$owner2":"$owner2outlookId", "$owner3":"$owner3outlookId", "$owner4":"$owner4outlookId"}"""
      ))

      when(appointmentRender.teamMembers).thenReturn(Set(
        AppointmentTeamMember(Member(Usercode(owner1), None, null), None),
        AppointmentTeamMember(Member(Usercode(owner2), None, null), Some(owner2outlookId))
      ))

      when(mockO365Service.deleteO365(owner3, s"events/$owner3outlookId", Json.obj())).thenReturn(Future.successful(Some(JsNull)))
      when(mockO365Service.postO365(owner1, "events", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj("Id" -> "4567"))))
      when(mockO365Service.patchO365(owner2, s"events/$owner2outlookId", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj("Id" -> "5678"))))

      when(mockOwnerService.setAppointmentOutlookId(appointmentId, Usercode(owner1), "4567")(auditLogContext)).thenReturn(Future.successful(Right(Owner(appointmentId, EntityType.Appointment, Usercode(owner1), Some("4567"), null))))

      job.execute(context)

      verify(mockO365Service, never()).deleteO365(Matchers.eq(owner4), Matchers.any(), Matchers.any())
      verify(mockOwnerService, never()).setAppointmentOutlookId(Matchers.any(), Matchers.eq(Usercode(owner2)), Matchers.any())(Matchers.any())
      verify(mockScheduler, never()).scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())
    }

    "handle various errors" in new Fixture {
      private val context = setupContext(Map(
        "appointmentId" -> appointmentId.toString,
        "owners" -> s"""{"owner1":"", "owner2":"", "owner3":"1234", "owner4":"2345", "owner5":"3456"}"""
      ))

      when(appointmentRender.teamMembers).thenReturn(Set(
        AppointmentTeamMember(Member(Usercode("owner1"), None, null), None),
        AppointmentTeamMember(Member(Usercode("owner2"), None, null), None),
        AppointmentTeamMember(Member(Usercode("owner3"), None, null), Some(null)),
        AppointmentTeamMember(Member(Usercode("owner4"), None, null), Some(null))
      ))

      when(mockO365Service.postO365("owner1", "events", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj())))
      when(mockO365Service.postO365("owner2", "events", expectedAppointmentJson)).thenReturn(Future.successful(None))

      when(mockO365Service.patchO365("owner3", "events/1234", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj())))
      when(mockO365Service.patchO365("owner4", "events/2345", expectedAppointmentJson)).thenReturn(Future.successful(None))

      when(mockO365Service.deleteO365("owner5", "events/3456", Json.obj())).thenReturn(Future.successful(None))

      private var jobDetail: JobDetail = _

      when(mockScheduler.scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())).thenAnswer((invocation: InvocationOnMock) => {
        jobDetail = invocation.getArguments.apply(0).asInstanceOf[JobDetail]
        null
      })

      job.execute(context)

      private val dataMap = jobDetail.getJobDataMap
      dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.appointmentId) mustBe appointmentId.toString
      private val ownersJson = Json.parse(dataMap.getString(UpdateAppointmentInOffice365Job.JobDataMapKeys.owners))
      private val ownersMap = ownersJson.as[Map[String, String]]
      ownersMap.keySet.size mustBe 5
      ownersMap("owner1") mustBe ""
      ownersMap("owner2") mustBe ""
      ownersMap("owner3") mustBe "1234"
      ownersMap("owner4") mustBe "2345"
      ownersMap("owner5") mustBe "3456"
      dataMap.getIntegerFromString("retries").intValue mustBe 1
    }

    "only push when opted-in" in new Fixture {
      val owner1 = "cusfal"
      val owner1outlookId = ""
      val owner2 = "cusebr"
      val owner2outlookId = "1234"
      val owner3 = "cuscao"
      val owner3outlookId = "2345"
      val owner4 = "curef"
      val owner4outlookId = ""
      private val context = setupContext(Map(
        "appointmentId" -> appointmentId.toString,
        "owners" -> s"""{"$owner1":"$owner1outlookId", "$owner2":"$owner2outlookId", "$owner3":"$owner3outlookId", "$owner4":"$owner4outlookId"}"""
      ))

      when(appointmentRender.teamMembers).thenReturn(Set(
        AppointmentTeamMember(Member(Usercode(owner1), None, null), None),
        AppointmentTeamMember(Member(Usercode(owner2), None, null), Some(owner2outlookId))
      ))

      when(mockO365Service.deleteO365(owner3, s"events/$owner3outlookId", Json.obj())).thenReturn(Future.successful(Some(JsNull)))
      when(mockO365Service.postO365(owner1, "events", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj("Id" -> "4567"))))
      when(mockO365Service.patchO365(owner2, s"events/$owner2outlookId", expectedAppointmentJson)).thenReturn(Future.successful(Some(Json.obj("Id" -> "5678"))))

      when(mockOwnerService.setAppointmentOutlookId(appointmentId, Usercode(owner1), "4567")(auditLogContext)).thenReturn(Future.successful(Right(Owner(appointmentId, EntityType.Appointment, Usercode(owner1), Some("4567"), null))))

      when(mockPreferencesService.get(Set(Usercode(owner1), Usercode(owner2), Usercode(owner3), Usercode(owner4)))).thenReturn(Future.successful(Right(Map(
        Usercode(owner1) -> UserPreferences.default, // Owner 1 not enabled
        Usercode(owner2) -> UserPreferences.default.copy(office365Enabled = true),
        Usercode(owner3) -> UserPreferences.default, // Owner 3 not enabled
        Usercode(owner4) -> UserPreferences.default.copy(office365Enabled = true),
      ))))

      job.execute(context)

      verify(mockO365Service, never()).postO365(Matchers.eq(owner1), Matchers.any(), Matchers.any()) // Owner 1 shouldn't be pushed even though they are added
      verify(mockO365Service, times(1)).patchO365(Matchers.eq(owner2), Matchers.any(), Matchers.any()) // Owner 2 should be pushed
      verify(mockO365Service, times(1)).deleteO365(Matchers.eq(owner3), Matchers.any(), Matchers.any()) // Owner 3 should be pushed as it's a delete and that should ignore that they're not enabled

      verify(mockOwnerService, never()).setAppointmentOutlookId(Matchers.any(), Matchers.eq(Usercode(owner2)), Matchers.any())(Matchers.any())
      verify(mockScheduler, never()).scheduleJob(Matchers.any[JobDetail](), Matchers.any[Trigger]())
    }

  }

}
