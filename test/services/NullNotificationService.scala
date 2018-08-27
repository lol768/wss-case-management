package services
import domain.{Enquiry, Message}
import helpers.ServiceResults.ServiceResult
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso.UniversityID

import scala.concurrent.Future

class NullNotificationService extends NotificationService {
  def activity = new Activity()

  def result = Future.successful(Right(activity))

  override def newRegistration(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def registrationInvite(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def newEnquiry(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def enquiryMessage(enquiry: Enquiry, message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def enquiryReassign(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
}
