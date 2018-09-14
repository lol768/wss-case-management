package domain

import java.util.UUID

import domain.dao.CaseDao.Case
import helpers.JavaTime
import warwick.sso._

object Fixtures {
  object users {
    val noUniId = Users.create(Usercode("nouniid"))
    val studentNewVisitor = Users.create(
      usercode = Usercode("student1"),
      universityId = Some(UniversityID("9900001")),
      student = true,
      undergraduate = true
    )

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
  }

  object messages {
    def newMessage(
      ownerId: UUID,
      ownerType: MessageOwner,
      sender: MessageSender = MessageSender.Client,
      teamMember: Option[Usercode] = None
    ) = Message(
      id = UUID.randomUUID(),
      text = "Hello",
      fileId = None,
      sender = sender,
      teamMember = teamMember,
      ownerId = ownerId,
      ownerType = ownerType
    )

    def newEnquiryMessage(enquiry: UUID) = newMessage(
      ownerId = enquiry,
      ownerType = MessageOwner.Enquiry
    )
  }

  object cases {
    def newCase(issueKey: Int = 1234): Case = Case(
      Some(UUID.randomUUID()),
      Some(IssueKey(IssueKeyType.Case, issueKey)),
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
      CaseCause.New
    )
  }
}
