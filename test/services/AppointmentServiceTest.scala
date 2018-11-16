package services

import java.time.Duration

import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.dao.ClientDao.StoredClient
import domain.dao.MemberDao.StoredMember
import domain.dao._
import helpers.DataFixture
import play.api.libs.json.Json
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.Future

class AppointmentServiceTest extends AbstractDaoTest {

  private val service = get[AppointmentService]

  class AppointmentFixture extends DataFixture[Appointment] {
    override def setup(): Appointment = {
      val now = JavaTime.offsetDateTime
      val stored = Fixtures.appointments.newStoredAppointment()
      val storedAppointmentClient = Fixtures.appointments.newStoredClient(stored.id)
      execWithCommit(
        (AppointmentDao.appointments.table += stored) andThen
        (ClientDao.clients.table += StoredClient(storedAppointmentClient.universityID, None, now)) andThen
        (AppointmentDao.appointmentClients.table += storedAppointmentClient) andThen
        (MemberDao.members.table += StoredMember(Usercode("mentalhealth-counsellor"), None, now)) andThen
        (Owner.owners.table += Owner(stored.id, Owner.EntityType.Appointment, Usercode("mentalhealth-counsellor"), None, JavaTime.offsetDateTime))
      )
      stored.asAppointment
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }

  "AppointmentServiceTest" should {
    "create" in withData(new AppointmentFixture) { _ =>
      val created = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), None, AppointmentType.FaceToFace, AppointmentPurpose.Consultation), Set(UniversityID("0672089")), Set(Usercode("u1234567")), Teams.Counselling, Set.empty, Usercode("u1234567")).serviceValue
      created.id must not be null
      created.key.string mustBe "APP-1000"
      created.duration mustBe Duration.ofMinutes(15)

      // Create a case to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase()))
      val c2 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase(1235)))

      val created2 = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.InitialAssessment), Set(UniversityID("0672089"), UniversityID("0672088")), Set(Usercode("u1234444")), Teams.WellbeingSupport, Set(c.id, c2.id), Usercode("u1234567")).serviceValue
      created2.id must not be created.id
      created2.key.string mustBe "APP-1001"
    }

    "find" in withData(new AppointmentFixture) { appointment =>
      // Can find by either UUID or IssueKey
      service.find(appointment.id).serviceValue.id mustBe appointment.id
      service.find(appointment.key).serviceValue.id mustBe appointment.id
    }

    "find by client" in withData(new AppointmentFixture) { _ =>
      val created = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), None, AppointmentType.FaceToFace, AppointmentPurpose.Consultation), Set(UniversityID("0672089")), Set(Usercode("u1234567")), Teams.Counselling, Set.empty, Usercode("u1234567")).serviceValue
      val created2 = service.create(AppointmentSave(JavaTime.offsetDateTime.minusHours(1), Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.InitialAssessment), Set(UniversityID("0672089"), UniversityID("0672088")), Set(Usercode("u1234444")), Teams.WellbeingSupport, Set.empty, Usercode("u1234567")).serviceValue

      service.findForClient(UniversityID("0672089")).serviceValue.map(_.appointment) mustBe Seq(created2, created)
      service.findForClient(UniversityID("0672088")).serviceValue.map(_.appointment) mustBe Seq(created2)
      service.findForClient(UniversityID("1122334")).serviceValue mustBe 'empty
    }

    "find for render" in withData(new AppointmentFixture) { _ =>
      val now = JavaTime.offsetDateTime

      DateTimeUtils.useMockDateTime(now.toInstant, () => {
        val singleClientNoCase = service.create(AppointmentSave(now, Duration.ofMinutes(15), None, AppointmentType.FaceToFace, AppointmentPurpose.Consultation), Set(UniversityID("0672089")), Set(Usercode("u1234567")), Teams.Counselling, Set.empty, Usercode("u1234567")).serviceValue

        service.findForRender(singleClientNoCase.key).serviceValue mustBe AppointmentRender(
          singleClientNoCase,
          Set(
            AppointmentClient(Client(UniversityID("0672089"), None, now), AppointmentState.Provisional, None, None)
          ),
          Set(
            AppointmentTeamMember(Member(Usercode("u1234567"), None, now), None)
          ),
          None,
          Set.empty,
        )
      })

      // Create cases to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase()))
      val c2 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase(1235)))

      DateTimeUtils.useMockDateTime(now.toInstant, () => {
        val multipleClientsMultipleTeamMembersWithCases = service.create(AppointmentSave(now, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.FollowUp), Set(UniversityID("0672089"), UniversityID("0672088")), Set(Usercode("u1234444"), Usercode("u1234555")), Teams.WellbeingSupport, Set(c.id, c2.id), Usercode("u1234567")).serviceValue

        service.findForRender(multipleClientsMultipleTeamMembersWithCases.key).serviceValue mustBe AppointmentRender(
          multipleClientsMultipleTeamMembersWithCases,
          Set(
            AppointmentClient(Client(UniversityID("0672088"), None, now), AppointmentState.Provisional, None, None),
            AppointmentClient(Client(UniversityID("0672089"), None, now), AppointmentState.Provisional, None, None)
          ),
          Set(
            AppointmentTeamMember(Member(Usercode("u1234444"), None, now), None),
            AppointmentTeamMember(Member(Usercode("u1234555"), None, now), None)
          ),
          None,
          Set(c.asCase, c2.asCase),
        )
      })
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

    "update cases" in withData(new AppointmentFixture) { a =>
      // Create a case to link to
      val c = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase())).asCase
      val c2 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase(1235))).asCase
      val c3 = execWithCommit(CaseDao.cases.insert(Fixtures.cases.newStoredCase(1236))).asCase

      val before = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.Consultation), Set(), Set(Usercode("u1234444")), Teams.WellbeingSupport, Set(c.id, c2.id), Usercode("u1234444")).serviceValue
      val after = service.update(before.id, AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.Consultation), Set(c.id, c3.id), Set(UniversityID("0672089"), UniversityID("0672088")), Set(Usercode("u1234555")), Usercode("u1234555"), before.lastUpdated).serviceValue

      service.findForRender(after.key).serviceValue.clientCases mustBe Set(c, c3)
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
      val multiClient = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.Consultation), Set(client1, client2), Set(Usercode("u1234444")), Teams.WellbeingSupport, Set.empty, Usercode("u1234444")).serviceValue

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
      val multiClient = service.create(AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(10), None, AppointmentType.Skype, AppointmentPurpose.Consultation), Set(client1, client2), Set(Usercode("u1234444")), Teams.WellbeingSupport, Set.empty, Usercode("u1234444")).serviceValue

      multiClient.state mustBe AppointmentState.Provisional

      val multiClientUpdate1 = service.clientAccept(multiClient.id, client1).serviceValue
      multiClientUpdate1.state mustBe AppointmentState.Accepted // Any client has accepted

      val multiClientUpdate2 = service.clientDecline(multiClient.id, client2, AppointmentCancellationReason.Clash).serviceValue
      multiClientUpdate2.state mustBe AppointmentState.Accepted // Any clients has accepted, even though one has declined

      val multiClientUpdate3 = service.clientDecline(multiClient.id, client1, AppointmentCancellationReason.UnableToAttend).serviceValue
      multiClientUpdate3.state mustBe AppointmentState.Provisional // All clients have declined
    }

    "search" in withData(new AppointmentFixture) { a =>
      val building = execWithCommit(LocationDao.buildings.insert(Fixtures.locations.newBuilding()))
      val r021 = execWithCommit(LocationDao.rooms.insert(Fixtures.locations.newRoom(building.id, name = "R0.21")))
      val r023 = execWithCommit(LocationDao.rooms.insert(Fixtures.locations.newRoom(building.id, name = "R0.23")))

      val appointmentR021 = service.create(
        AppointmentSave(JavaTime.offsetDateTime, Duration.ofMinutes(15), Some(r021.id), AppointmentType.FaceToFace, AppointmentPurpose.Consultation), Set(UniversityID("0672089")), Set(Usercode("u1234567")), Teams.Counselling, Set.empty, Usercode("u1234444")
      ).serviceValue

      service.search(AppointmentSearchQuery(roomID = Some(r021.id)), 5).serviceValue mustBe Seq(appointmentR021)
      service.search(AppointmentSearchQuery(roomID = Some(r023.id)), 5).serviceValue mustBe Seq()
    }
  }
}
