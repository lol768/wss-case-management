package helpers

import domain.SitsProfile
import helpers.ServiceResults.ServiceResult
import helpers.caching.CacheElement
import services.tabula.ProfileService
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID

import scala.concurrent.Future

class MockProfileService extends ProfileService {

  override def getProfile(universityID: UniversityID)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Option[SitsProfile]]]] =
    Future.successful(CacheElement(Right(None), 0, 0, 0))

  override def getProfiles(universityIDs: Set[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Map[UniversityID, SitsProfile]]] =
    Future.successful(Right(Map()))

}
