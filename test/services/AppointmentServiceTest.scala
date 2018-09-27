package services

import java.time.Duration

import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.{AbstractDaoTest, AppointmentDao, CaseDao}
import helpers.{DataFixture, JavaTime}
import play.api.libs.json.Json
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.Future

class AppointmentServiceTest extends AbstractDaoTest {

  private val service = get[AppointmentService]

  class AppointmentFixture extends DataFixture[Appointment] {
    override def setup(): Appointment = {
      val stored = Fixtures.appointments.newStoredAppointment()
      execWithCommit(
        AppointmentDao.appointments.insert(stored) andThen
        AppointmentDao.appointmentClients.insert(Fixtures.appointments.newStoredClient(stored.id))
      )
      stored.asAppointment
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "AppointmentServiceTest" should {
    "create" in withData(new AppointmentFixture) { _ =>
      val created = service.create(AppointmentSave("Meeting", JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, None).serviceValue
      created.id must not be null
      created.key.string mustBe "APP-1000"
      created.duration mustBe Duration.ofMinutes(15)

      // Create a case to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase()))

      val created2 = service.create(AppointmentSave("Skype call", JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, Some(c.id.get)).serviceValue
      created2.id must not be created.id
      created2.key.string mustBe "APP-1001"
    }

    "find" in withData(new AppointmentFixture) { appointment =>
      // Can find by either UUID or IssueKey
      service.find(appointment.id).serviceValue.id mustBe appointment.id
      service.find(appointment.key).serviceValue.id mustBe appointment.id
    }

    "find by client" in withData(new AppointmentFixture) { _ =>
      val created = service.create(AppointmentSave("App 1", JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, None).serviceValue
      val created2 = service.create(AppointmentSave("App 2", JavaTime.offsetDateTime.minusHours(1), Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, None).serviceValue

      service.findForClient(UniversityID("0672089")).serviceValue.map(_.appointment) mustBe Seq(created2, created)
      service.findForClient(UniversityID("0672088")).serviceValue.map(_.appointment) mustBe Seq(created2)
      service.findForClient(UniversityID("1122334")).serviceValue mustBe 'empty
    }

    "find for render" in withData(new AppointmentFixture) { _ =>
      val singleClientNoCase = service.create(AppointmentSave("Meeting", JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, None).serviceValue

      service.findForRender(singleClientNoCase.key).serviceValue mustBe AppointmentRender(
        singleClientNoCase,
        Set(
          AppointmentClient(UniversityID("0672089"), AppointmentState.Provisional, None)
        ),
        None
      )

      // Create a case to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase()))

      val multipleClientsWithCase = service.create(AppointmentSave("Skype call", JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, Some(c.id.get)).serviceValue

      service.findForRender(multipleClientsWithCase.key).serviceValue mustBe AppointmentRender(
        multipleClientsWithCase,
        Set(
          AppointmentClient(UniversityID("0672088"), AppointmentState.Provisional, None),
          AppointmentClient(UniversityID("0672089"), AppointmentState.Provisional, None)
        ),
        Some(c)
      )
    }

    "find recently viewed" in withData(new AppointmentFixture) { a =>
      implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("cuscav")))

      val auditService = get[AuditService]

      auditService.audit('AppointmentView, a.id.toString, 'Appointment, Json.obj())(Future.successful(Right(()))).serviceValue
      auditService.audit('AppointmentView, a.id.toString, 'Appointment, Json.obj())(Future.successful(Right(()))).serviceValue
      auditService.audit('AppointmentView, a.id.toString, 'Appointment, Json.obj())(Future.successful(Right(()))).serviceValue
      auditService.audit('AppointmentView, a.id.toString, 'Appointment, Json.obj())(Future.successful(Right(()))).serviceValue

      service.findRecentlyViewed(Usercode("cuscav"), 5).serviceValue mustBe Seq(a)
    }
  }
}
