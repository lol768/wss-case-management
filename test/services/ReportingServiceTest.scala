package services

import java.util.concurrent.atomic.AtomicInteger

import domain._
import domain.dao.AppointmentDao.AppointmentCase
import domain.dao.CaseDao.StoredCaseClient
import domain.dao.ClientDao.StoredClient
import domain.dao.{AbstractDaoTest, AppointmentDao, CaseDao, ClientDao}
import helpers.DataFixture
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.{UniversityID, Usercode}

class ReportingServiceTest extends AbstractDaoTest {

  override implicit def auditLogContext: AuditLogContext = super.auditLogContext.copy(usercode = Some(Usercode("custard")))

  private val reportingService = get[ReportingService]
  private val caseDelayInMinutes = 90L

  class ReportingFixture() extends DataFixture[Enquiry] {
    private val enquiryService = get[EnquiryService]
    private val seq = new AtomicInteger(4242)
    private val uniId = UniversityID("1234567")

    private def makeCaseAndApptForEnquiry(enq: Enquiry, state: IssueState): Unit = {
      val timestamp = enq.created.plusMinutes(caseDelayInMinutes)
      val issueKey = seq.getAndIncrement
      
      DateTimeUtils.useMockDateTime(timestamp, () => {
        val newCase = execWithCommit(
          CaseDao.cases.insert(Fixtures.cases.newStoredCase(issueKey).copy(
            state = state,
            originalEnquiry = Some(enq.id),
            created = timestamp,
            team = enq.team
          ))
        ).asCase

        execWithCommit(
          CaseDao.caseClients.insert(StoredCaseClient(newCase.id, uniId))
        )

        val appt = execWithCommit(
          AppointmentDao.appointments.insert(Fixtures.appointments.newStoredAppointment(issueKey))
        ).asAppointment

        execWithCommit(
          AppointmentCase.appointmentCases.insert(Fixtures.appointments.newAppointmentCase(appt.id, newCase.id))
        )
      })
    }

    override def setup(): Enquiry = {
      val es = EnquirySave(universityID = uniId, subject = "Enquiry", team = Teams.WellbeingSupport, state = IssueState.Open)
      val msg = MessageSave("Hello", MessageSender.Client, None)

      execWithCommit(
        ClientDao.clients.insert(StoredClient(uniId, Some("Jonathan Testman")))
      )

      enquiryService.save(es.copy(state = IssueState.Closed), msg.copy(), Nil)
      val closedEnq = enquiryService.save(es.copy(state = IssueState.Closed), msg.copy(), Nil).serviceValue
      makeCaseAndApptForEnquiry(closedEnq, IssueState.Closed)
      
      enquiryService.save(es.copy(), msg.copy(), Nil).serviceValue
      enquiryService.save(es.copy(), msg.copy(), Nil).serviceValue
      enquiryService.save(es.copy(team = Teams.Disability), msg.copy(), Nil).serviceValue
      
      val openEnq = enquiryService.save(es.copy(team = Teams.MentalHealth), msg.copy(), Nil).serviceValue
      makeCaseAndApptForEnquiry(openEnq, IssueState.Open)
      openEnq
    }

    override def teardown(): Unit = {
      execWithCommit(Fixtures.schemas.truncateAndReset)
    }
  }
  
  
  "querying enquiries" should {
    "count first-time enquirers over a date range" in {
      withData(new ReportingFixture) { enq =>
        val created = enq.created
        val start = created.minusMinutes(1)
        val end = created.plusMinutes(1)

        reportingService.countFirstEnquiries(start, end, None).futureValue mustBe 3 // one per team
        reportingService.countFirstEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countFirstEnquiries(start, end, Some(Teams.Counselling)).futureValue mustBe 0

        val outofScopeStart = created.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countFirstEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
      }
    }
    "count enquiries over a date range" in {
      withData(new ReportingFixture) { enq =>
        val created = enq.created
        val start = created.minusMinutes(1)
        val end = created.plusMinutes(1)

        reportingService.countClosedEnquiries(start, end, None).futureValue mustBe 2
        reportingService.countClosedEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 2
        reportingService.countClosedEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0

        reportingService.countOpenedEnquiries(start, end, None).futureValue mustBe 4
        reportingService.countOpenedEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 2
        reportingService.countOpenedEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 1
        reportingService.countOpenedEnquiries(start, end, Some(Teams.Counselling)).futureValue mustBe 0

        val outofScopeStart = created.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countOpenedEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
      }
    }

    "count case conversions over a date range" in {
      withData(new ReportingFixture) { enq =>
        val caseCreated = enq.created.plusMinutes(caseDelayInMinutes)
        val start = caseCreated.minusMinutes(1)
        val end = caseCreated.plusMinutes(1)

        reportingService.countClosedCasesFromEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countClosedCasesFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countClosedCasesFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 0
        
        reportingService.countOpenedCasesFromEnquiries(start, end, None).futureValue mustBe 1
        reportingService.countOpenedCasesFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 0
        reportingService.countOpenedCasesFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 1

        val outofScopeStart = caseCreated.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countOpenedCasesFromEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
      }
    }
    
    "count case conversions with non-cancelled appointments over a date range" in {
      withData(new ReportingFixture) { enq =>
        val caseCreated = enq.created.plusMinutes(caseDelayInMinutes)
        val start = caseCreated.minusMinutes(1)
        val end = caseCreated.plusMinutes(1)
        
        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, None).futureValue mustBe 2
        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, Some(Teams.WellbeingSupport)).futureValue mustBe 1
        reportingService.countCasesWithAppointmentsFromEnquiries(start, end, Some(Teams.MentalHealth)).futureValue mustBe 1
        
        val outofScopeStart = caseCreated.minusHours(1)
        val outofScopeEnd = outofScopeStart.plusMinutes(1)
        reportingService.countOpenedCasesFromEnquiries(outofScopeStart, outofScopeEnd, None).futureValue mustBe 0
      }
    }
  }
}
