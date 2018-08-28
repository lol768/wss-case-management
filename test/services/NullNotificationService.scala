package services
import domain.{Enquiry, Message}
import helpers.ServiceResults.ServiceResult
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso.UniversityID

import scala.concurrent.Future

class NullNotificationService extends NotificationService {
  def activity = new Activity()

  def result = Future.successful(Right(activity))

  override def newRegistration(universityID: UniversityID): Future[ServiceResult[Activity]] = result
  override def registrationInvite(universityID: UniversityID): Future[ServiceResult[Activity]] = result
  override def newEnquiry(enquiry: Enquiry): Future[ServiceResult[Activity]] = result
  override def enquiryMessage(enquiry: Enquiry, message: Message): Future[ServiceResult[Activity]] = result
  override def enquiryReassign(enquiry: Enquiry): Future[ServiceResult[Activity]] = result
}
