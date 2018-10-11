package services

import java.time.Duration

import akka.Done
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
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
      val created = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, Set.empty).serviceValue
      created.id must not be null
      created.key.string mustBe "APP-1000"
      created.duration mustBe Duration.ofMinutes(15)

      // Create a case to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase()))
      val c2 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase(1235)))

      val created2 = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, Set(c.id.get, c2.id.get)).serviceValue
      created2.id must not be created.id
      created2.key.string mustBe "APP-1001"
    }

    "find" in withData(new AppointmentFixture) { appointment =>
      // Can find by either UUID or IssueKey
      service.find(appointment.id).serviceValue.id mustBe appointment.id
      service.find(appointment.key).serviceValue.id mustBe appointment.id
    }

    "find by client" in withData(new AppointmentFixture) { _ =>
      val created = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, Set.empty).serviceValue
      val created2 = service.create(AppointmentSave(JavaTime.offsetDateTime.minusHours(1), Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, Set.empty).serviceValue

      service.findForClient(UniversityID("0672089")).serviceValue.map(_.appointment) mustBe Seq(created2, created)
      service.findForClient(UniversityID("0672088")).serviceValue.map(_.appointment) mustBe Seq(created2)
      service.findForClient(UniversityID("1122334")).serviceValue mustBe 'empty
    }

    "find for render" in withData(new AppointmentFixture) { _ =>
      val singleClientNoCase = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(NamedLocation("My office")), Usercode("u1234567"), AppointmentType.FaceToFace), Set(UniversityID("0672089")), Teams.Counselling, Set.empty).serviceValue

      service.findForRender(singleClientNoCase.key).serviceValue mustBe AppointmentRender(
        singleClientNoCase,
        Set(
          AppointmentClient(UniversityID("0672089"), AppointmentState.Provisional, None)
        ),
        Set.empty,
        Seq()
      )

      // Create cases to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase()))
      val c2 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newCase(1235)))

      val multipleClientsWithCases = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(UniversityID("0672089"), UniversityID("0672088")), Teams.WellbeingSupport, Set(c.id.get, c2.id.get)).serviceValue

      // Add some notes
      val note1 = service.addNote(multipleClientsWithCases.id, AppointmentNoteSave("Note 1 test", Usercode("cusfal"))).serviceValue
      val note2 = service.addNote(multipleClientsWithCases.id, AppointmentNoteSave("Note 2 test", Usercode("cusfal"))).serviceValue

      service.findForRender(multipleClientsWithCases.key).serviceValue mustBe AppointmentRender(
        multipleClientsWithCases,
        Set(
          AppointmentClient(UniversityID("0672088"), AppointmentState.Provisional, None),
          AppointmentClient(UniversityID("0672089"), AppointmentState.Provisional, None)
        ),
        Set(c, c2),
        Seq(note1, note2)
      )

      // remove a case
      val multipleClientsWithCase = service.update(multipleClientsWithCases.id, AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype ), Set(c.id.get), Set(UniversityID("0672089"), UniversityID("0672088")), multipleClientsWithCases.lastUpdated).serviceValue
      service.findForRender(multipleClientsWithCase.key).serviceValue mustBe AppointmentRender(
        multipleClientsWithCase,
        Set(
          AppointmentClient(UniversityID("0672088"), AppointmentState.Provisional, None),
          AppointmentClient(UniversityID("0672089"), AppointmentState.Provisional, None)
        ),
        Set(c),
        Seq(note1, note2)
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
      updated.state mustBe AppointmentState.Accepted

      val client1 = UniversityID("0672089")
      val client2 = UniversityID("0672088")
      val multiClient = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(client1, client2), Teams.WellbeingSupport, Set.empty).serviceValue

      multiClient.state mustBe AppointmentState.Provisional

      val multiClientUpdate1 = service.clientAccept(multiClient.id, client1).serviceValue
      multiClientUpdate1.state mustBe AppointmentState.Accepted // Any client has accepted

      val multiClientUpdate2 = service.clientAccept(multiClient.id, client2).serviceValue
      multiClientUpdate2.state mustBe AppointmentState.Accepted // All clients have accepted
    }

    "not set appointment state to cancelled if a client declines" in withData(new AppointmentFixture) { a =>
      val universityID = UniversityID("1234567") // Must match Fixtures.appointments.newStoredClient

      a.state mustBe AppointmentState.Provisional

      val updated = service.clientDecline(a.id, universityID, AppointmentCancellationReason.Clash).serviceValue
      updated.id mustBe a.id

      // This is a no-op because a client declining doesn't cancel the appointment - only a team member can do that
      updated.lastUpdated mustBe a.lastUpdated
      updated.state mustBe AppointmentState.Provisional
    }

    "update appointment state when a client declines an appointment" in withData(new AppointmentFixture) { a =>
      val universityID = UniversityID("1234567") // Must match Fixtures.appointments.newStoredClient

      a.state mustBe AppointmentState.Provisional

      // Accepting the appointment should accept it
      val updated = service.clientAccept(a.id, universityID).serviceValue
      updated.state mustBe AppointmentState.Accepted

      // Declining a confirmed appointment should set it back to provisional
      val updated2 = service.clientDecline(a.id, universityID, AppointmentCancellationReason.Clash).serviceValue
      updated2.state mustBe AppointmentState.Provisional

      val client1 = UniversityID("0672089")
      val client2 = UniversityID("0672088")
      val multiClient = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, Usercode("u1234444"), AppointmentType.Skype), Set(client1, client2), Teams.WellbeingSupport, Set.empty).serviceValue

      multiClient.state mustBe AppointmentState.Provisional

      val multiClientUpdate1 = service.clientAccept(multiClient.id, client1).serviceValue
      multiClientUpdate1.state mustBe AppointmentState.Accepted // Any client has accepted

      val multiClientUpdate2 = service.clientDecline(multiClient.id, client2, AppointmentCancellationReason.Clash).serviceValue
      multiClientUpdate2.state mustBe AppointmentState.Accepted // Any clients has accepted, even though one has declined

      val multiClientUpdate3 = service.clientDecline(multiClient.id, client1, AppointmentCancellationReason.UnableToAttend).serviceValue
      multiClientUpdate3.state mustBe AppointmentState.Provisional // All clients have declined
    }

    "get and set appointment notes" in withData(new AppointmentFixture) { a =>
      service.getNotes(a.id).serviceValue mustBe 'empty

      val n1 = service.addNote(a.id, AppointmentNoteSave(
        text = "I just called to say I love you",
        teamMember = Usercode("cuscav")
      )).serviceValue

      val n2 = service.addNote(a.id, AppointmentNoteSave(
        text = "Jim came in to tell me that Peter needed a chat",
        teamMember = Usercode("cusebr")
      )).serviceValue

      service.getNotes(a.id).serviceValue mustBe Seq(n2, n1) // Newest first

      val n1Updated = service.updateNote(a.id, n1.id, AppointmentNoteSave(
        text = "Jim's not really bothered",
        teamMember = Usercode("cusebr")
      ), n1.lastUpdated).serviceValue

      service.getNotes(a.id).serviceValue mustBe Seq(n2, n1Updated)

      service.deleteNote(a.id, n2.id, n2.lastUpdated).serviceValue mustBe Done

      service.getNotes(a.id).serviceValue mustBe Seq(n1Updated)
    }

    "search" in withData(new AppointmentFixture) { a =>
      service.addNote(a.id, AppointmentNoteSave(
        text = "Here's some text to search",
        teamMember = Usercode("cuscav")
      )).serviceValue

      service.search(AppointmentSearchQuery(query = Some("some text")), 5).serviceValue mustBe Seq(a)

      service.search(AppointmentSearchQuery(location = Some(NamedLocation("W0.01"))), 5).serviceValue mustBe Seq(a)
      service.search(AppointmentSearchQuery(location = Some(NamedLocation("ACCR"))), 5).serviceValue mustBe Seq()
    }
  }
}
