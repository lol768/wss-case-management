package services

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import domain._
import domain.dao.AppointmentDao.AppointmentCase
import domain.dao.ClientDao.StoredClient
import domain.dao.EnquiryDao.StoredEnquiry
import domain.dao.MemberDao.StoredMember
import domain.dao._
import helpers.DataFixture
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.{UniversityID, Usercode}

class ReportingServiceTest extends AbstractDaoTest {

  override implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("custard")))

  private val reportingService = get[ReportingService]
  private val firstUniId = UniversityID("1234567")
  private val secondUniId = UniversityID("7654321")
  private val seq = new AtomicInteger(4242)

  class EnquiryFixture() extends DataFixture[Seq[Enquiry]] {
    
    override def setup(): Seq[Enquiry] = {
      val zeroTime = OffsetDateTime.of(2019, 3, 1, 10, 0, 0 , 0, ZoneOffset.UTC)
      
      val storedClient1 = StoredClient(firstUniId, Some("Jonathan Testman"))
      val storedClient2 = StoredClient(secondUniId, Some("Hans Fulov-Tests"))
      DateTimeUtils.useMockDateTime(zeroTime, () => {
        execWithCommit(ClientDao.clients.insertAll(Seq(storedClient1, storedClient2)))
      })

      (0L to 6L).flatMap { dayOffset =>
        Teams.all.flatMap { team =>
          val creationTime = zeroTime.plusDays(dayOffset).plusMinutes(team.id.length.longValue)
          // let's assume the opened enquiries are arbitrarily modified three days later,
          // to test that we use dates of creation (the discriminator for opened enqs) and not version
          val versionTime = zeroTime.plusDays(3L)

          val closedCreationTime = creationTime.plusHours(3L)
          val closedVersionTime = creationTime.plusHours(4L)

          val se1 = StoredEnquiry(
            UUID.randomUUID,
            IssueKey(IssueKeyType.Enquiry, seq.getAndIncrement),
            firstUniId,
            s"Enquiry for ${team.name}",
            team,
            IssueState.Open,
            versionTime,
            creationTime
          )
          val e1 = se1.asEnquiry(storedClient1.asClient)

          val se2 = StoredEnquiry(
            UUID.randomUUID,
            IssueKey(IssueKeyType.Enquiry, seq.getAndIncrement),
            secondUniId,
            s"Another enquiry for ${team.name}",
            team,
            IssueState.Open,
            versionTime,
            creationTime
          )
          val e2 = se2.asEnquiry(storedClient2.asClient)

          val se3 = StoredEnquiry(
            UUID.randomUUID,
            IssueKey(IssueKeyType.Enquiry, seq.getAndIncrement),
            firstUniId,
            s"Enquiry for ${team.name}",
            team,
            IssueState.Closed,
            closedVersionTime,
            closedCreationTime
          )
          val e3 = se3.asEnquiry(storedClient1.asClient)

          // version timestamp from StoredEnquiry is ignored in insert.
          // Use MockDateTime to make sure they're set to expected values.
          DateTimeUtils.useMockDateTime(versionTime, () => {
            execWithCommit(
              EnquiryDao.enquiries.insert(se1) andThen
                EnquiryDao.enquiries.insert(se2)
            )
          })

          DateTimeUtils.useMockDateTime(closedVersionTime, () => {
            execWithCommit(
              EnquiryDao.enquiries.insert(se3)
            )
          })

          Seq(e1, e2, e3)
        }
      }
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }


  class CaseAndAppointmentFixture() extends DataFixture[(Seq[Case], Seq[Appointment])] {
    override def setup(): (Seq[Case], Seq[Appointment]) = {
      val timestamp = OffsetDateTime.of(2019, 3, 1, 10, 0, 0 , 0, ZoneOffset.UTC)
      val storedClient = StoredClient(firstUniId, Some("Jonathan Testman"))
      val storedMember = StoredMember(Usercode("custard"), Some("Vries McCounsellor"), timestamp)
      
      val openedCaseEnq = Fixtures.cases.newStoredCase(seq.getAndIncrement).copy(
        created = timestamp,
        version = timestamp,
        team = Teams.WellbeingSupport,
        state = IssueState.Open,
        originalEnquiry = Some(UUID.randomUUID)
      )

      val closedCaseEnq = Fixtures.cases.newStoredCase(seq.getAndIncrement).copy(
        created = timestamp,
        version = timestamp,
        team = Teams.WellbeingSupport,
        state = IssueState.Closed,
        originalEnquiry = Some(UUID.randomUUID)
      )

      val openedCase = Fixtures.cases.newStoredCase(seq.getAndIncrement).copy(
        created = timestamp,
        version = timestamp,
        team = Teams.WellbeingSupport,
        state = IssueState.Open
      )

      val closedCase = Fixtures.cases.newStoredCase(seq.getAndIncrement).copy(
        created = timestamp,
        version = timestamp,
        team = Teams.WellbeingSupport,
        state = IssueState.Closed
      )
      
      val cases = Seq(openedCase, closedCase, openedCaseEnq, closedCaseEnq)
      val apptsAndLinks = cases.flatMap { linkedCase =>
        AppointmentState.values.map { state =>
          val appt = Fixtures.appointments.newStoredAppointment(seq.getAndIncrement).copy(
            start = timestamp,
            created = timestamp,
            version = timestamp,
            state = state,
            team = Teams.WellbeingSupport
          )

          val apptCase = Fixtures.appointments.newAppointmentCase(appt.id, linkedCase.id)
          val apptClient = Fixtures.appointments.newStoredClient(appt.id).copy(universityID = firstUniId, state = state)
          val apptOwner = AppointmentOwner(appt.id, storedMember.usercode, timestamp)
          
          (appt, apptCase, apptClient, apptOwner)
        }
      }

      DateTimeUtils.useMockDateTime(timestamp, () => {
        execWithCommit(
          ClientDao.clients.insert(storedClient) andThen
          MemberDao.members.insert(storedMember) andThen
          DBIO.sequence(cases.map(c => CaseDao.cases.insert(c))) andThen
          DBIO.sequence(apptsAndLinks.map{ case (appt, apptCase, client, owner) =>
            AppointmentDao.appointments.insert(appt) andThen
            AppointmentCase.appointmentCases.insert(apptCase) andThen
            AppointmentDao.appointmentClients.insert(client) andThen
            Owner.owners.insert(owner)
          })
        )
      })

      (cases.map(c => c.asCase), apptsAndLinks.map { case (appt, _, _, _) => appt.asAppointment })
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }
  

  "querying enquiries" should {
    "count first-time enquirers" in {
      withData(new EnquiryFixture) { enqs =>
        val start = enqs.minBy(_.created).created
        val end = start.plusDays(6).plusHours(6)

//        reportingService.countFirstEnquiries(start, end, None).futureValue mustBe 8 // two users, so two per team
//        reportingService.countFirstEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 2
//
//        val outofScopeStart = start.minusHours(1)
//        val outofScopeEnd = outofScopeStart.plusMinutes(1)
//        reportingService.countFirstEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0

        val dailies = reportingService.firstEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        
        dailies.size mustBe 7
        dailies.head.day.isEqual(start.toLocalDate) mustBe true
        dailies.head.value mustBe 2
        dailies.tail.foreach(_.value mustBe 0)
      }
    }

    "count" in {
      withData(new EnquiryFixture) { enqs =>
        val start = enqs.minBy(_.created).created
        val end = start.plusDays(6).plusHours(6)
        
        reportingService.countClosedEnquiries(start, end, None).futureValue mustBe 28 // seven days, four teams
        reportingService.countClosedEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 7

        reportingService.countOpenedEnquiries(start, end, None).futureValue mustBe 56 // seven days, four teams, two users
        reportingService.countOpenedEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 14

        val outofScopeStart = start.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countClosedEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countOpenedEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        
        val closedDailies = reportingService.closedEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        closedDailies.size mustBe 7
        closedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        closedDailies.foreach(_.value mustBe 1)

        val openedDailies = reportingService.openedEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        openedDailies.size mustBe 7
        openedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        openedDailies.foreach(_.value mustBe 2)
      }
    }
  }

  "querying cases" should {
    "count enquiry-derived conversions" in {
      withData(new CaseAndAppointmentFixture) { case (cases, _) =>
        val start = cases.minBy(_.created).created
        val end = start.plusDays(6).plusHours(1)

        reportingService.countClosedCasesFromEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countClosedCasesFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countClosedCasesFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        reportingService.countOpenedCasesFromEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countOpenedCasesFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countOpenedCasesFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        val outofScopeStart = start.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countClosedCasesFromEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countOpenedCasesFromEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0

        val closedDailies = reportingService.closedCasesFromEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        closedDailies.size mustBe 7
        closedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        closedDailies.head.value mustBe 1
        closedDailies.tail.foreach(_.value mustBe 0)

        val openedDailies = reportingService.openedCasesFromEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        openedDailies.size mustBe 7
        openedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        openedDailies.head.value mustBe 1
        openedDailies.tail.foreach(_.value mustBe 0)
      }
    }

    "count non-enquiry-derived conversions" in {
      withData(new CaseAndAppointmentFixture) { case (cases, _) =>
        val start = cases.minBy(_.created).created
        val end = start.plusDays(6).plusHours(1)

        reportingService.countClosedCasesWithoutEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countClosedCasesWithoutEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countClosedCasesWithoutEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        reportingService.countOpenedCasesWithoutEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countOpenedCasesWithoutEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countOpenedCasesWithoutEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        val outofScopeStart = start.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countClosedCasesWithoutEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countOpenedCasesWithoutEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0

        val closedDailies = reportingService.closedCasesWithoutEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        closedDailies.size mustBe 7
        closedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        closedDailies.head.value mustBe 1
        closedDailies.tail.foreach(_.value mustBe 0)

        val openedDailies = reportingService.openedCasesWithoutEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        openedDailies.size mustBe 7
        openedDailies.head.day.isEqual(start.toLocalDate) mustBe true
        openedDailies.head.value mustBe 1
        openedDailies.tail.foreach(_.value mustBe 0)
      }
    }
  }

  "querying appointments" should {
    "count enquiry-derived conversions with non-cancelled appointments" in {
      withData(new CaseAndAppointmentFixture) { case (_, appts) =>
        val start = appts.minBy(_.created).created
        val end = start.plusDays(6).plusHours(1)

        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, None).futureValue mustBe 2
        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 2
        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        val outofScopeStart = start.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countCasesWithAppointmentsFromEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0

        val dailies = reportingService.casesWithAppointmentsFromEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        dailies.size mustBe 7
        dailies.head.day.isEqual(start.toLocalDate) mustBe true
        dailies.head.value mustBe 2
        dailies.tail.foreach(_.value mustBe 0)
      }
    }

    "count non-enquiry-derived conversions with non-cancelled appointments" in {
      withData(new CaseAndAppointmentFixture) { case (_, appts) =>
        val start = appts.minBy(_.created).created
        val end = start.plusDays(6).plusHours(1)

        reportingService.countCasesWithAppointmentsWithoutEnquiries(start, end, None).futureValue mustBe 2
        reportingService.countCasesWithAppointmentsWithoutEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 2
        reportingService.countCasesWithAppointmentsWithoutEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0
        
        val outofScopeStart = start.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countCasesWithAppointmentsWithoutEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0

        val dailies = reportingService.casesWithAppointmentsWithoutEnquiriesByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        dailies.size mustBe 7
        dailies.head.day.isEqual(start.toLocalDate) mustBe true
        dailies.head.value mustBe 2
        dailies.tail.foreach(_.value mustBe 0)
      }
    }

    "count by state" in {
      withData(new CaseAndAppointmentFixture) { case (_, appts) =>
        val start = appts.minBy(_.created).created
        val end = start.plusDays(6).plusHours(1)
 
        reportingService.countProvisionalAppointments(start, end, None).futureValue mustBe 4
        reportingService.countProvisionalAppointments(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 4
        reportingService.countProvisionalAppointments(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0
        
        reportingService.countAcceptedAppointments(start, end, None).futureValue mustBe 4
        reportingService.countAcceptedAppointments(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 4
        reportingService.countAcceptedAppointments(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0
        
        reportingService.countAttendedAppointments(start, end, None).futureValue mustBe 4
        reportingService.countAttendedAppointments(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 4
        reportingService.countAttendedAppointments(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0
        
        reportingService.countCancelledAppointments(start, end, None).futureValue mustBe 4
        reportingService.countCancelledAppointments(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 4
        reportingService.countCancelledAppointments(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        // appt search uses all-day searches out of the box, so need to force scope further out than other tests
        val outofScopeStart = start.minusDays(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countProvisionalAppointments(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countAcceptedAppointments(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countAttendedAppointments(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
        reportingService.countCancelledAppointments(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
 
        val dailies = reportingService.provisionalAppointmentsByDay(start.toLocalDate, end.toLocalDate, Teams.WellbeingSupport).serviceValue
        dailies.size mustBe 7
        dailies.head.day.isEqual(start.toLocalDate) mustBe true
        dailies.head.value mustBe 4
        dailies.tail.foreach(_.value mustBe 0)
      }
    }
  }
}
