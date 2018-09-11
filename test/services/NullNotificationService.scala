package services
import domain.dao.CaseDao
import domain.{Enquiry, MessageSender}
import helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.Future

class NullNotificationService extends NotificationService {
  def activity = new Activity()

  def result = Future.successful(Right(activity))

  override def newRegistration(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def registrationInvite(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def newEnquiry(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def enquiryMessage(enquiry: Enquiry, sender: MessageSender)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def enquiryReassign(enquiry: Enquiry)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def newCaseOwner(newOwners: Set[Usercode], clientCase: CaseDao.Case)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
  override def caseReassign(clientCase: CaseDao.Case)(implicit t: TimingContext): Future[ServiceResult[Activity]] = result
}
