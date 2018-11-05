package domain

import java.time.{Duration, LocalDate}
import java.util.UUID

import domain.dao.AppointmentDao.{StoredAppointment, StoredAppointmentClient}
import domain.dao.CaseDao.Case
import domain.dao.DSADao.DSAApplication
import domain.dao.LocationDao.{StoredBuilding, StoredRoom}
import domain.dao._
import warwick.core.helpers.JavaTime
import warwick.sso._

object Fixtures {
  object users {
    val noUniId: User = Users.create(Usercode("nouniid"))
    private def undergrad(i: Int): User = Users.create(
      usercode = Usercode(s"student$i"),
      universityId = Some(UniversityID(f"${9900000+i}%d")),
      student = true,
      undergraduate = true
    )
    val studentNewVisitor: User = undergrad(1)
    val studentCaseClient: User = undergrad(2)

    private val baseStaff: User = Users.create(
      usercode = null,
      universityId = None,
      staff = true
    )

    // Staff users here correspond with webgroup members defined in test.conf
    val ss1: User = baseStaff.copy(
      usercode = Usercode("ss1"),
      universityId = Some(UniversityID("1700001")),
      name = Name(Some("Studentsupport"), Some("User1"))
    )

    // Staff users here correspond with webgroup members defined in test.conf
    val ss2: User = baseStaff.copy(
      usercode = Usercode("ss2"),
      universityId = Some(UniversityID("1700002")),
      name = Name(Some("Studentsupport"), Some("User2"))
    )

    val mh1: User = baseStaff.copy(
      usercode = Usercode("mh1"),
      universityId = Some(UniversityID("1700011")),
      name = Name(Some("MentalHealth"), Some("User1"))
    )

    val mh2: User = baseStaff.copy(
      usercode = Usercode("mh2"),
      universityId = Some(UniversityID("1700012")),
      name = Name(Some("MentalHealth"), Some("User2"))
    )
  }

  object profiles {
    val mat: SitsProfile = SitsProfile(
      universityID = UniversityID("0672089"),
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

    val undergraduate = SitsProfile(
      universityID = UniversityID("1672089"),
      usercode = Usercode("u1672089"),
      fullName = "Mathew Mannion",
      dateOfBirth = LocalDate.of(1984, 8, 19),
      phoneNumber = None,
      warwickEmail = Some("m.mannion@warwick.ac.uk"),
      alternateEmail = None,
      address = None,
      residence = None,
      department = SitsDepartment("CH", "Chemistry"),
      course = None,
      route = None,
      courseStatus = None,
      enrolmentStatus = None,
      attendance = None,
      group = Some(StudentGroup.Undergraduate),
      yearOfStudy = Some(YearOfStudy(1, "1")),
      startDate = None,
      endDate = None,
      nationality = None,
      dualNationality = None,
      tier4VisaRequired = None,
      disability = None,
      photo = None,
      personalTutors = Nil,
      researchSupervisors = Nil,
      userType = UserType.Student
    )
  }

  object messages {
    def newMessage(
      ownerId: UUID,
      ownerType: MessageOwner,
      client: UniversityID,
      sender: MessageSender = MessageSender.Client,
      teamMember: Option[Usercode] = None,
      team: Option[Team] = None
    ) = Message(
      id = UUID.randomUUID(),
      text = "Hello",
      sender = sender,
      client = client,
      teamMember = teamMember,
      team = team,
      ownerId = ownerId,
      ownerType = ownerType
    )

    def newEnquiryMessage(enquiry: UUID, client: UniversityID): Message = newMessage(
      ownerId = enquiry,
      ownerType = MessageOwner.Enquiry,
      client = client
    )
  }

  object cases {
    def newCaseNoId(): Case = Case(
      None,
      None,
      "Mental health assessment",
      JavaTime.offsetDateTime,
      Teams.MentalHealth,
      JavaTime.offsetDateTime,
      IssueState.Open,
      None,
      None,
      None,
      None,
      None,
      None,
      Some(CaseType.MentalHealthAssessment),
      CaseCause.New,
      None
    )

    def newCase(issueKey: Int = 1234): Case = newCaseNoId().copy(
      id = Some(UUID.randomUUID()),
      key = Some(IssueKey(IssueKeyType.Case, issueKey)),
      dsaApplication = None
    )

    def newDSAApplicationNoId() = DSAApplicationAndTypes(
      application = DSAApplication(
        id = None,
        applicationDate = None,
        fundingApproved = None,
        confirmationDate = None,
        ineligibilityReason = None,
        version = JavaTime.offsetDateTime
      ),
      fundingTypes = Set()
    )
  }

  object appointments {
    def newStoredAppointment(issueKey: Int = 1234): StoredAppointment = StoredAppointment(
      UUID.randomUUID(),
      IssueKey(IssueKeyType.Appointment, issueKey),
      JavaTime.offsetDateTime
        .withHour(14).withMinute(0).withSecond(0).withNano(0),
      Duration.ofMinutes(45),
      None,
      Teams.MentalHealth,
      AppointmentType.FaceToFace,
      AppointmentPurpose.Consultation,
      AppointmentState.Provisional,
      None,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime,
    )

    def newStoredClient(appointmentID: UUID): StoredAppointmentClient = StoredAppointmentClient(
      UniversityID("1234567"),
      appointmentID: UUID,
      AppointmentState.Provisional,
      None,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime,
    )
  }

  object locations {
    def newBuilding(name: String = "Ramphal", wai2GoID: Int = 12345): StoredBuilding = StoredBuilding(
      UUID.randomUUID(),
      name,
      wai2GoID,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime,
    )

    def newRoom(buildingID: UUID, name: String = "R0.21", wai2GoID: Option[Int] = None): StoredRoom = StoredRoom(
      UUID.randomUUID(),
      buildingID,
      name,
      wai2GoID,
      available = true,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime,
    )
  }

  object schemas {
    import domain.ExtendedPostgresProfile.api._
    def truncateAndReset =
      CaseDao.caseTags.table.delete andThen
      CaseDao.caseTags.versionsTable.delete andThen
      CaseDao.caseClients.table.delete andThen
      CaseDao.caseClients.versionsTable.delete andThen
      CaseDao.caseLinks.table.delete andThen
      CaseDao.caseLinks.versionsTable.delete andThen
      CaseDao.caseDocuments.table.delete andThen
      CaseDao.caseDocuments.versionsTable.delete andThen
      CaseDao.caseNotes.table.delete andThen
      CaseDao.caseNotes.versionsTable.delete andThen
      UploadedFileDao.uploadedFiles.table.delete andThen
      UploadedFileDao.uploadedFiles.versionsTable.delete andThen
      AuditEvent.auditEvents.delete andThen
      AppointmentDao.appointmentNotes.table.delete andThen
      AppointmentDao.appointmentNotes.versionsTable.delete andThen
      AppointmentDao.appointmentClients.table.delete andThen
      AppointmentDao.appointmentClients.versionsTable.delete andThen
      AppointmentDao.appointments.table.delete andThen
      AppointmentDao.appointments.versionsTable.delete andThen
      CaseDao.cases.table.delete andThen
      CaseDao.cases.versionsTable.delete andThen
      EnquiryDao.enquiries.table.delete andThen
      EnquiryDao.enquiries.versionsTable.delete andThen
      Message.messages.table.delete andThen
      Message.messages.versionsTable.delete andThen
      Owner.owners.table.delete andThen
      Owner.owners.versionsTable.delete andThen
      ClientDao.clients.table.delete andThen
      ClientDao.clients.versionsTable.delete andThen
      MemberDao.members.table.delete andThen
      MemberDao.members.versionsTable.delete andThen
      LocationDao.rooms.table.delete andThen
      LocationDao.rooms.versionsTable.delete andThen
      LocationDao.buildings.table.delete andThen
      LocationDao.buildings.versionsTable.delete andThen
      sql"ALTER SEQUENCE SEQ_CASE_ID RESTART WITH 1000".asUpdate andThen
      sql"ALTER SEQUENCE SEQ_APPOINTMENT_KEY RESTART WITH 1000".asUpdate andThen
      sql"ALTER SEQUENCE SEQ_ENQUIRY_KEY RESTART WITH 1000".asUpdate
  }
}
