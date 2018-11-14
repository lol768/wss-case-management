package domain

import java.time.{Duration, LocalDate}
import java.util.UUID

import domain.dao.AppointmentDao.{StoredAppointment, StoredAppointmentClient}
import domain.dao.CaseDao.{StoredCase, StoredCaseFields}
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
      address = None,
      department = SitsDepartment("IN", "IT Services"),
      course = None,
      attendance = None,
      group = None,
      yearOfStudy = None,
      startDate = None,
      endDate = None,
      nationality = None,
      dualNationality = None,
      tier4VisaRequired = None,
      disability = None,
      disabilityFundingStatus = None,
      jobTitle = Some("Service Owner"),
      photo = None,
      userType = UserType.Staff
    )

    val undergraduate = SitsProfile(
      universityID = UniversityID("1672089"),
      usercode = Usercode("u1672089"),
      fullName = "Mathew Mannion",
      dateOfBirth = LocalDate.of(1984, 8, 19),
      phoneNumber = None,
      warwickEmail = Some("m.mannion@warwick.ac.uk"),
      address = None,
      department = SitsDepartment("CH", "Chemistry"),
      course = None,
      attendance = None,
      group = Some(StudentGroup.Undergraduate),
      yearOfStudy = Some(YearOfStudy(1, "1")),
      startDate = None,
      endDate = None,
      nationality = None,
      dualNationality = None,
      tier4VisaRequired = None,
      disability = None,
      disabilityFundingStatus = None,
      jobTitle = Some("Undergraduate - full-time"),
      photo = None,
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
    def newStoredCase(issueKey: Int = 1234): StoredCase =
      StoredCase(
        UUID.randomUUID(),
        IssueKey(IssueKeyType.Case, issueKey),
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
        None,
        CaseCause.New,
        None,
        StoredCaseFields(
          List(),
          List(),
          List(),
          None,
          List(),
          None,
          None
        ),
      )

    def newDSAApplicationSave() = DSAApplicationSave(
      applicationDate = None,
      fundingApproved = None,
      confirmationDate = None,
      ineligibilityReason = None,
      fundingTypes = Set(),
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
      List(),
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
      RegistrationDao.Registration.registrations.table.delete andThen
      RegistrationDao.Registration.registrations.versionsTable.delete andThen
      sql"ALTER SEQUENCE SEQ_CASE_ID RESTART WITH 1000".asUpdate andThen
      sql"ALTER SEQUENCE SEQ_APPOINTMENT_KEY RESTART WITH 1000".asUpdate andThen
      sql"ALTER SEQUENCE SEQ_ENQUIRY_KEY RESTART WITH 1000".asUpdate
  }
}
