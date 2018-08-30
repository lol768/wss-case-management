package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.CaseDao.Case
import domain.dao.{CaseDao, DaoRunner}
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CaseServiceImpl])
trait CaseService {
  def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]]
}

class CaseServiceImpl @Inject() (
  daoRunner: DaoRunner,
  dao: CaseDao
)(
  implicit ec: ExecutionContext
) extends CaseService {

  override def find(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Case]] =
    daoRunner.run(dao.find(id)).map(Right(_))

}
