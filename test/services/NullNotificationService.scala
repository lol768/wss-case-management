package services

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
  override def newEnquiry(enquiryKey: IssueKey)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def caseMessage(`case`: Case, client: UniversityID, sender: MessageSender)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def enquiryReassign(enquiry: Enquiry)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def newCaseOwner(newOwners: Set[Usercode], clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def caseReassign(clientCase: Case)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def newAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def appointmentConfirmation(appointment: Appointment, teamMembers: Set[Usercode], clientState: AppointmentState)(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def cancelledAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def changedAppointment(clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
  override def appointmentReminder(appointment: Appointment, clients: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Activity]] = result
}
