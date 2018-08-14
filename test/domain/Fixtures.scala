package domain

import java.util.UUID

import warwick.sso.{UniversityID, Usercode, Users}

object Fixtures {
  object users {
    val noUniId = Users.create(Usercode("nouniid"))
    val studentNewVisitor = Users.create(
      usercode = Usercode("student1"),
      universityId = Some(UniversityID("9900001")),
      student = true,
      undergraduate = true
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
}
