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
        (AppointmentDao.appointments.table += stored) andThen
        (AppointmentDao.appointmentClients.table += Fixtures.appointments.newStoredClient(stored.id))
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

    "update appointment state when a client accepts an appointment" in withData(new AppointmentFixture) { a =>
      val universityID = UniversityID("1234567") // Must match Fixtures.appointments.newStoredClient

      a.state mustBe AppointmentState.Provisional

      val updated = service.clientAccept(a.id, universityID).serviceValue
      updated.id mustBe a.id
      updated.lastUpdated mustNot be (a.lastUpdated)
      updated.state mustBe AppointmentState.Confirmed

      val client1 = UniversityID("0672089")
      val client2 = UniversityID("0672088")
      val multiClient = service.create(AppointmentSave("Skype call", JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(client1, client2), Teams.WellbeingSupport, None).serviceValue

      multiClient.state mustBe AppointmentState.Provisional

      val multiClientUpdate1 = service.clientAccept(multiClient.id, client1).serviceValue
      multiClientUpdate1.state mustBe AppointmentState.Confirmed // Any client has accepted

      val multiClientUpdate2 = service.clientAccept(multiClient.id, client2).serviceValue
      multiClientUpdate2.state mustBe AppointmentState.Confirmed // All clients have accepted
    }

    "update appointment state when a client rejects an appointment" in withData(new AppointmentFixture) { a =>
      val universityID = UniversityID("1234567") // Must match Fixtures.appointments.newStoredClient

      a.state mustBe AppointmentState.Provisional

      val updated = service.clientReject(a.id, universityID, AppointmentCancellationReason.Clash).serviceValue
      updated.id mustBe a.id

      // This is a no-op because a client rejecting doesn't cancel the appointment - only a team member can do that
      updated.lastUpdated mustBe a.lastUpdated
      updated.state mustBe AppointmentState.Provisional

      // Accepting the appointment should confirm it
      val updated2 = service.clientAccept(a.id, universityID).serviceValue
      updated2.state mustBe AppointmentState.Confirmed

      // Rejecting a confirmed appointment should set it back to provisional
      val updated3 = service.clientReject(a.id, universityID, AppointmentCancellationReason.Clash).serviceValue
      updated3.state mustBe AppointmentState.Provisional

      val client1 = UniversityID("0672089")
      val client2 = UniversityID("0672088")
      val multiClient = service.create(AppointmentSave("Skype call", JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(client1, client2), Teams.WellbeingSupport, None).serviceValue

      multiClient.state mustBe AppointmentState.Provisional

      val multiClientUpdate1 = service.clientAccept(multiClient.id, client1).serviceValue
      multiClientUpdate1.state mustBe AppointmentState.Confirmed // Any client has accepted

      val multiClientUpdate2 = service.clientReject(multiClient.id, client2, AppointmentCancellationReason.Clash).serviceValue
      multiClientUpdate2.state mustBe AppointmentState.Confirmed // Any clients has accepted, even though one has rejected

      val multiClientUpdate3 = service.clientReject(multiClient.id, client1, AppointmentCancellationReason.UnableToAttend).serviceValue
      multiClientUpdate3.state mustBe AppointmentState.Provisional // All clients have rejected
    }
  }
}
