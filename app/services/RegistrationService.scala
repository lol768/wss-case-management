package services

import com.google.inject.ImplementedBy
import domain.Registrations
import domain.dao.RegistrationDao
import javax.inject.Inject
import warwick.sso.UniversityID

import scala.concurrent.Future

@ImplementedBy(classOf[RegistrationServiceImpl])
trait RegistrationService {

  def save(studentSupportRegistration: Registrations.StudentSupport): Future[Int]

  def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]]

}

class RegistrationServiceImpl @Inject()(
  dao: RegistrationDao
) extends RegistrationService {

  override def save(studentSupportRegistration: Registrations.StudentSupport): Future[Int] =
    dao.save(studentSupportRegistration)

  override def getStudentSupport(universityID: UniversityID): Future[Option[Registrations.StudentSupport]] =
    dao.getStudentSupport(universityID)

}
