package services
import domain.dao.CaseDao
import domain.dao.CaseDao.Case
import domain._
import helpers.ServiceResults.ServiceResult
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.Future

class NullNotificationService extends NotificationService {
  def activity = new Activity()

  def result = Future.successful(Right(activity))

  override def newRegistration(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def registrationInvite(universityID: UniversityID)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def newEnquiry(enquiry: Enquiry)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def caseMessage(`case`: Case, client: UniversityID, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def enquiryReassign(enquiry: Enquiry)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def newCaseOwner(newOwners: Set[Usercode], clientCase: CaseDao.Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def caseReassign(clientCase: CaseDao.Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def newAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def appointmentConfirmation(appointment: Appointment, clientState: AppointmentState)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
}
